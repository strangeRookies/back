package com.strange.safety.camera.overlay;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OverlayRelayService {

    private static final Logger log = LoggerFactory.getLogger(OverlayRelayService.class);
    private static final String DEFAULT_SCHEMA_VERSION = "1.1";
    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0", "1.1");
    private static final String MESSAGE_TYPE = "overlay";

    private final CameraRepository cameraRepository;
    private final CorporateCameraRepository corporateCameraRepository;
    private final OverlayBroadcastService broadcastService;
    private final Clock clock;
    private final long staleAfterMillis;

    private final ConcurrentMap<String, OverlaySnapshot> latestOverlays = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sourceTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> receivedAtMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> revisions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> publishedRevisions = new ConcurrentHashMap<>();
    private final Map<String, Object> cameraLocks = new ConcurrentHashMap<>();

    @Autowired
    public OverlayRelayService(
            CameraRepository cameraRepository,
            CorporateCameraRepository corporateCameraRepository,
            OverlayBroadcastService broadcastService,
            @Value("${mqtt.overlay-stale-after-ms:2000}") long staleAfterMillis) {
        this(cameraRepository, corporateCameraRepository, broadcastService, Clock.systemUTC(), staleAfterMillis);
    }

    OverlayRelayService(
            CameraRepository cameraRepository,
            CorporateCameraRepository corporateCameraRepository,
            OverlayBroadcastService broadcastService,
            Clock clock) {
        this(cameraRepository, corporateCameraRepository, broadcastService, clock, 2_000L);
    }

    OverlayRelayService(
            CameraRepository cameraRepository,
            CorporateCameraRepository corporateCameraRepository,
            OverlayBroadcastService broadcastService,
            Clock clock,
            long staleAfterMillis) {
        this.cameraRepository = cameraRepository;
        this.corporateCameraRepository = corporateCameraRepository;
        this.broadcastService = broadcastService;
        this.clock = clock;
        this.staleAfterMillis = staleAfterMillis;
    }

    public boolean accept(OverlayMessage message) {
        CameraMatch match = validateAndMatch(message);
        if (match == null) {
            return false;
        }

        String cameraLoginId = match.cameraLoginId();
        OverlayMessage matchedMessage = message.withCameraLoginId(cameraLoginId);
        synchronized (lockFor(cameraLoginId)) {
            Long previousTimestamp = sourceTimestamps.get(cameraLoginId);
            if (previousTimestamp != null && message.timestampMs() <= previousTimestamp) {
                log.debug("Discarding stale overlay: cameraLoginId={}, timestampMs={}, previousTimestampMs={}",
                        cameraLoginId, message.timestampMs(), previousTimestamp);
                return false;
            }

            sourceTimestamps.put(cameraLoginId, message.timestampMs());
            receivedAtMillis.put(cameraLoginId, clock.millis());
            latestOverlays.put(cameraLoginId, new OverlaySnapshot(matchedMessage, match.targetId(), match.corporate()));
            incrementRevision(cameraLoginId);
            return true;
        }
    }

    @Scheduled(fixedRateString = "${mqtt.overlay-publish-interval-ms:125}")
    public void publishLatest() {
        long now = clock.millis();
        for (String cameraLoginId : latestOverlays.keySet()) {
            OverlaySnapshot snapshot = prepareForPublish(cameraLoginId, now);
            if (snapshot != null) {
                broadcastService.broadcast(snapshot.message(), snapshot.targetId(), snapshot.corporate());
            }
        }
    }

    public void clear(String cameraLoginId) {
        OverlaySnapshot cleared = clearSnapshot(cameraLoginId, clock.millis(), true);
        if (cleared != null) {
            broadcastService.broadcast(cleared.message(), cleared.targetId(), cleared.corporate());
        }
    }

    public void clearAll() {
        for (String cameraLoginId : latestOverlays.keySet()) {
            clear(cameraLoginId);
        }
    }

    OverlayMessage latest(String cameraLoginId) {
        OverlaySnapshot snapshot = latestOverlays.get(cameraLoginId);
        return snapshot == null ? null : snapshot.message();
    }

    private OverlaySnapshot prepareForPublish(String cameraLoginId, long now) {
        synchronized (lockFor(cameraLoginId)) {
            OverlaySnapshot current = latestOverlays.get(cameraLoginId);
            if (current == null) {
                return null;
            }

            Long receivedAt = receivedAtMillis.get(cameraLoginId);
            if (receivedAt != null
                    && now - receivedAt >= staleAfterMillis
                    && !current.message().events().isEmpty()) {
                current = clearSnapshot(cameraLoginId, now, false);
            }

            long revision = revisions.getOrDefault(cameraLoginId, 0L);
            if (publishedRevisions.getOrDefault(cameraLoginId, 0L) >= revision) {
                return null;
            }
            publishedRevisions.put(cameraLoginId, revision);
            return current;
        }
    }

    private OverlaySnapshot clearSnapshot(String cameraLoginId, long timestampMs, boolean markPublished) {
        synchronized (lockFor(cameraLoginId)) {
            OverlaySnapshot current = latestOverlays.get(cameraLoginId);
            if (current == null || current.message().events() == null || current.message().events().isEmpty()) {
                return null;
            }

            OverlayMessage cleared = new OverlayMessage(
                    current.message().schemaVersion() == null ? DEFAULT_SCHEMA_VERSION : current.message().schemaVersion(),
                    MESSAGE_TYPE,
                    timestampMs,
                    current.message().streamId(),
                    current.message().cameraLoginId(),
                    current.message().frameWidth(),
                    current.message().frameHeight(),
                    List.of());
            OverlaySnapshot clearedSnapshot = new OverlaySnapshot(cleared, current.targetId(), current.corporate());
            latestOverlays.put(cameraLoginId, clearedSnapshot);
            long revision = incrementRevision(cameraLoginId);
            if (markPublished) {
                publishedRevisions.put(cameraLoginId, revision);
            }
            return clearedSnapshot;
        }
    }

    private CameraMatch validateAndMatch(OverlayMessage message) {
        if (message == null
                || !SUPPORTED_SCHEMA_VERSIONS.contains(message.schemaVersion())
                || !MESSAGE_TYPE.equals(message.messageType())
                || message.resolvedCameraLoginId() == null
                || message.resolvedCameraLoginId().isBlank()
                || message.frameWidth() <= 0
                || message.frameHeight() <= 0
                || message.events() == null) {
            log.warn("Discarding invalid overlay header");
            return null;
        }
        for (OverlayEvent event : message.events()) {
            if (event == null || !isValidBox(event.resolvedBoundingBox(), message.frameWidth(), message.frameHeight())) {
                log.warn("Discarding overlay with invalid bounding box: cameraLoginId={}, timestampMs={}",
                        message.resolvedCameraLoginId(), message.timestampMs());
                return null;
            }
        }

        String cameraLoginId = message.resolvedCameraLoginId();
        Optional<Camera> camera = cameraRepository
                .findFirstByCameraLoginIdAndStatusOrderByIdDesc(cameraLoginId, CameraStatus.ACTIVE);
        if (camera.isPresent() && camera.get().getFacility() != null && camera.get().getFacility().getId() != null) {
            Long facilityId = camera.get().getFacility().getId();
            log.info("Overlay camera matched cameraLoginId={} facilityId={}", cameraLoginId, facilityId);
            return new CameraMatch(cameraLoginId, facilityId, false);
        }

        Optional<CorporateCamera> corporateCamera = corporateCameraRepository
                .findFirstByCameraLoginIdAndStatusOrderByIdDesc(cameraLoginId, CameraStatus.ACTIVE);
        if (corporateCamera.isPresent()
                && corporateCamera.get().getCompanyProfile() != null
                && corporateCamera.get().getCompanyProfile().getId() != null) {
            Long companyProfileId = corporateCamera.get().getCompanyProfile().getId();
            log.info("Overlay camera matched cameraLoginId={} companyProfileId={}", cameraLoginId, companyProfileId);
            return new CameraMatch(cameraLoginId, companyProfileId, true);
        }

        log.warn("Overlay discarded unknown cameraLoginId={}", cameraLoginId);
        return null;
    }

    private boolean isValidBox(BoundingBox box, int frameWidth, int frameHeight) {
        if (box == null
                || !Double.isFinite(box.x())
                || !Double.isFinite(box.y())
                || !Double.isFinite(box.width())
                || !Double.isFinite(box.height())) {
            return false;
        }
        return box.x() >= 0
                && box.y() >= 0
                && box.width() >= 0
                && box.height() >= 0
                && box.x() + box.width() <= frameWidth
                && box.y() + box.height() <= frameHeight;
    }

    private Object lockFor(String cameraLoginId) {
        return cameraLocks.computeIfAbsent(cameraLoginId, ignored -> new Object());
    }

    private long incrementRevision(String cameraLoginId) {
        return revisions.merge(cameraLoginId, 1L, Long::sum);
    }

    private record CameraMatch(String cameraLoginId, Long targetId, boolean corporate) {
    }

    private record OverlaySnapshot(OverlayMessage message, Long targetId, boolean corporate) {
    }
}
