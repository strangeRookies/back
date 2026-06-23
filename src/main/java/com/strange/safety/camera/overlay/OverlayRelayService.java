package com.strange.safety.camera.overlay;

import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OverlayRelayService {

    private static final Logger log = LoggerFactory.getLogger(OverlayRelayService.class);
    private static final String SCHEMA_VERSION = "1.0";
    private static final String MESSAGE_TYPE = "overlay";
    private static final long STALE_AFTER_MILLIS = 1_000L;

    private final CameraRepository cameraRepository;
    private final CorporateCameraRepository corporateCameraRepository;
    private final OverlayBroadcastService broadcastService;
    private final Clock clock;

    private final ConcurrentMap<String, OverlayMessage> latestOverlays = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> sourceTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> receivedAtMillis = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> revisions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> publishedRevisions = new ConcurrentHashMap<>();
    private final Map<String, Object> cameraLocks = new ConcurrentHashMap<>();

    @Autowired
    public OverlayRelayService(
            CameraRepository cameraRepository,
            CorporateCameraRepository corporateCameraRepository,
            OverlayBroadcastService broadcastService) {
        this(cameraRepository, corporateCameraRepository, broadcastService, Clock.systemUTC());
    }

    OverlayRelayService(
            CameraRepository cameraRepository,
            CorporateCameraRepository corporateCameraRepository,
            OverlayBroadcastService broadcastService,
            Clock clock) {
        this.cameraRepository = cameraRepository;
        this.corporateCameraRepository = corporateCameraRepository;
        this.broadcastService = broadcastService;
        this.clock = clock;
    }

    public boolean accept(OverlayMessage message) {
        if (!isValid(message)) {
            return false;
        }

        String streamId = message.streamId();
        synchronized (lockFor(streamId)) {
            Long previousTimestamp = sourceTimestamps.get(streamId);
            if (previousTimestamp != null && message.timestampMs() <= previousTimestamp) {
                log.debug("Discarding stale overlay: streamId={}, timestampMs={}, previousTimestampMs={}",
                        streamId, message.timestampMs(), previousTimestamp);
                return false;
            }

            sourceTimestamps.put(streamId, message.timestampMs());
            receivedAtMillis.put(streamId, clock.millis());
            latestOverlays.put(streamId, message);
            incrementRevision(streamId);
            return true;
        }
    }

    @Scheduled(fixedRateString = "${mqtt.overlay-publish-interval-ms:125}")
    public void publishLatest() {
        long now = clock.millis();
        for (String streamId : latestOverlays.keySet()) {
            OverlayMessage message = prepareForPublish(streamId, now);
            if (message != null) {
                broadcastService.broadcast(message);
            }
        }
    }

    public void clear(String streamId) {
        OverlayMessage cleared = clearSnapshot(streamId, clock.millis(), true);
        if (cleared != null) {
            broadcastService.broadcast(cleared);
        }
    }

    public void clearAll() {
        for (String streamId : latestOverlays.keySet()) {
            clear(streamId);
        }
    }

    OverlayMessage latest(String streamId) {
        return latestOverlays.get(streamId);
    }

    private OverlayMessage prepareForPublish(String streamId, long now) {
        synchronized (lockFor(streamId)) {
            OverlayMessage current = latestOverlays.get(streamId);
            if (current == null) {
                return null;
            }

            Long receivedAt = receivedAtMillis.get(streamId);
            if (receivedAt != null
                    && now - receivedAt >= STALE_AFTER_MILLIS
                    && !current.events().isEmpty()) {
                current = clearSnapshot(streamId, now, false);
            }

            long revision = revisions.getOrDefault(streamId, 0L);
            if (publishedRevisions.getOrDefault(streamId, 0L) >= revision) {
                return null;
            }
            publishedRevisions.put(streamId, revision);
            return current;
        }
    }

    private OverlayMessage clearSnapshot(String streamId, long timestampMs, boolean markPublished) {
        synchronized (lockFor(streamId)) {
            OverlayMessage current = latestOverlays.get(streamId);
            if (current == null || current.events() == null || current.events().isEmpty()) {
                return null;
            }

            OverlayMessage cleared = new OverlayMessage(
                    SCHEMA_VERSION,
                    MESSAGE_TYPE,
                    timestampMs,
                    current.streamId(),
                    current.frameWidth(),
                    current.frameHeight(),
                    List.of());
            latestOverlays.put(streamId, cleared);
            long revision = incrementRevision(streamId);
            if (markPublished) {
                publishedRevisions.put(streamId, revision);
            }
            return cleared;
        }
    }

    private boolean isValid(OverlayMessage message) {
        if (message == null
                || !SCHEMA_VERSION.equals(message.schemaVersion())
                || !MESSAGE_TYPE.equals(message.messageType())
                || message.streamId() == null
                || message.streamId().isBlank()
                || message.frameWidth() <= 0
                || message.frameHeight() <= 0
                || message.events() == null) {
            log.warn("Discarding invalid overlay header");
            return false;
        }
        if (!isRegisteredCamera(message.streamId())) {
            log.warn("Discarding overlay for unknown or inactive camera: streamId={}", message.streamId());
            return false;
        }
        for (OverlayEvent event : message.events()) {
            if (event == null || !isValidBox(event.boundingBox(), message.frameWidth(), message.frameHeight())) {
                log.warn("Discarding overlay with invalid bounding box: streamId={}, timestampMs={}",
                        message.streamId(), message.timestampMs());
                return false;
            }
        }
        return true;
    }

    private boolean isRegisteredCamera(String streamId) {
        return cameraRepository
                .findFirstByCameraLoginIdAndStatusOrderByIdDesc(streamId, CameraStatus.ACTIVE)
                .isPresent()
                || corporateCameraRepository
                .findFirstByCameraLoginIdAndStatusOrderByIdDesc(streamId, CameraStatus.ACTIVE)
                .isPresent();
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

    private Object lockFor(String streamId) {
        return cameraLocks.computeIfAbsent(streamId, ignored -> new Object());
    }

    private long incrementRevision(String streamId) {
        return revisions.merge(streamId, 1L, Long::sum);
    }
}
