package com.strange.safety.vlm.snapshotassist;

import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Side-channel snapshot VLM assist. Never creates or mutates primary AlertEvent judgments.
 */
@Service
public class SnapshotAssistService {

    private final SnapshotAssistProperties properties;
    private final SnapshotAssistStore store;
    private final SnapshotAssistAnalyzer analyzer;

    public SnapshotAssistService(
            SnapshotAssistProperties properties,
            SnapshotAssistStore store,
            SnapshotAssistAnalyzer analyzer
    ) {
        this.properties = properties;
        this.store = store;
        this.analyzer = analyzer;
    }

    public boolean isEnabled() {
        return properties.effectivelyEnabled();
    }

    public boolean isServiceTokenValid(String token) {
        String expected = properties.serviceToken();
        return expected != null && !expected.isBlank() && expected.equals(token);
    }

    public SnapshotAssistRecord submit(
            String eventId,
            String cameraLoginId,
            byte[] jpegBytes,
            SnapshotAssistContext context
    ) throws IOException {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new IllegalArgumentException("empty snapshot");
        }
        if (looksLikeSyntheticDemo(jpegBytes)) {
            SnapshotAssistRecord failed = SnapshotAssistRecord.pending(eventId, cameraLoginId, "")
                    .failed("synthetic_or_invalid_snapshot_blocked");
            store.update(failed);
            return failed;
        }
        SnapshotAssistRecord saved = store.saveSnapshot(eventId, nullToEmpty(cameraLoginId), jpegBytes);
        if (context != null) {
            store.saveContext(eventId, context);
        }
        if (saved.status() == SnapshotAssistStatus.PENDING) {
            analyzer.analyzeAsync(eventId);
        }
        return saved;
    }

    public java.util.Optional<SnapshotAssistRecord> get(String eventId) {
        return store.find(eventId);
    }

    /** Synchronous analyze for unit tests. */
    public SnapshotAssistRecord analyzeNow(String eventId) throws IOException {
        return analyzer.analyzeNow(eventId);
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

    static boolean looksLikeSyntheticDemo(byte[] bytes) {
        if (bytes.length < 100) {
            return true;
        }
        return !(bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}