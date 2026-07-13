package com.strange.safety.vlm.snapshotassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

/**
 * Side-channel snapshot VLM assist. Never creates or mutates primary AlertEvent judgments.
 */
@Service
public class SnapshotAssistService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotAssistService.class);

    private final SnapshotAssistProperties properties;
    private final SnapshotAssistStore store;
    private final SnapshotAssistBroadcastService broadcastService;

    public SnapshotAssistService(
            SnapshotAssistProperties properties,
            SnapshotAssistStore store,
            SnapshotAssistBroadcastService broadcastService
    ) {
        this.properties = properties;
        this.store = store;
        this.broadcastService = broadcastService;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public boolean isServiceTokenValid(String token) {
        String expected = properties.serviceToken();
        return expected != null && !expected.isBlank() && expected.equals(token);
    }

    /**
     * Accept JPEG for eventId. Idempotent for duplicate submits.
     * Returns PENDING record immediately; analysis continues async.
     */
    public SnapshotAssistRecord submit(
            String eventId,
            String cameraLoginId,
            byte[] jpegBytes
    ) throws IOException {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IllegalArgumentException("empty snapshot");
        }
        // Hard-block synthetic demo payloads on live assist path
        if (looksLikeSyntheticDemo(jpegBytes)) {
            SnapshotAssistRecord failed = SnapshotAssistRecord.pending(eventId, cameraLoginId, "")
                    .failed("synthetic_or_invalid_snapshot_blocked");
            store.update(failed);
            broadcast(failed);
            return failed;
        }
        SnapshotAssistRecord saved = store.saveSnapshot(eventId, nullToEmpty(cameraLoginId), jpegBytes);
        if (saved.status() == SnapshotAssistStatus.PENDING) {
            analyzeAsync(eventId);
        } else {
            broadcast(saved);
        }
        return saved;
    }

    public Optional<SnapshotAssistRecord> get(String eventId) {
        return store.find(eventId);
    }

    @Async
    public void analyzeAsync(String eventId) {
        try {
            analyzeNow(eventId);
        } catch (Exception ex) {
            log.warn("[SnapshotAssist] analyze failed eventId={}: {}", eventId, ex.getMessage());
            store.find(eventId).ifPresent(rec -> {
                try {
                    SnapshotAssistRecord failed = rec.failed(ex.getMessage() == null ? "analyze_error" : ex.getMessage());
                    store.update(failed);
                    broadcast(failed);
                } catch (IOException ignored) {
                }
            });
        }
    }

    /** Synchronous analyze for unit tests. */
    public SnapshotAssistRecord analyzeNow(String eventId) throws IOException {
        SnapshotAssistRecord rec = store.find(eventId)
                .orElseThrow(() -> new IllegalArgumentException("unknown eventId"));
        if (rec.status() == SnapshotAssistStatus.SUCCESS || rec.status() == SnapshotAssistStatus.FAILED) {
            return rec;
        }
        String key = properties.geminiApiKey() == null ? "" : properties.geminiApiKey().trim();
        if (key.isEmpty()) {
            SnapshotAssistRecord failed = rec.failed("gemini_key_missing");
            store.update(failed);
            broadcast(failed);
            return failed;
        }
        if (isFakeApiKey(key)) {
            SnapshotAssistRecord failed = rec.failed("fake_api_key_blocked");
            store.update(failed);
            broadcast(failed);
            return failed;
        }
        if (properties.mockAnalyze()) {
            // Offline-safe SUCCESS path only when explicitly enabled (not production default for real Gemini claims)
            SnapshotAssistRecord ok = rec.success(
                    "스냅샷 보조 분석(mock): 이벤트 프레임에서 사람이 바닥 근처에 있는 것으로 보입니다. 기존 안전 알림을 대체하지 않습니다."
            );
            store.update(ok);
            broadcast(ok);
            return ok;
        }
        // Real Gemini vision call is env-gated; without network/key this path is not used in CI.
        SnapshotAssistRecord failed = rec.failed("gemini_live_call_not_configured_for_this_environment");
        store.update(failed);
        broadcast(failed);
        return failed;
    }

    private void broadcast(SnapshotAssistRecord rec) {
        broadcastService.publish(SnapshotAssistResultMessage.of(
                rec.eventId(),
                rec.cameraLoginId(),
                rec.status(),
                rec.summaryKo(),
                rec.errorMessage()
        ));
    }

    static boolean isFakeApiKey(String key) {
        String k = key.trim().toLowerCase();
        return k.isEmpty()
                || k.equals("fake")
                || k.equals("fake-api-key")
                || k.startsWith("fake-")
                || k.contains("placeholder")
                || k.equals("test")
                || k.equals("changeme")
                || k.startsWith("sk-fake")
                || k.equals("your_api_key_here");
    }

    /** Reject obviously non-JPEG or tiny demo stubs. */
    static boolean looksLikeSyntheticDemo(byte[] bytes) {
        if (bytes.length < 100) {
            return true;
        }
        // JPEG SOI
        return !(bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
