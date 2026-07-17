package com.strange.safety.vlm.snapshotassist;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vlm.snapshot-assist")
public record SnapshotAssistProperties(
        boolean enabled,
        String serviceToken,
        String storageRoot,
        int retentionDays,
        boolean mockAnalyze,
        boolean forceMock,
        String geminiApiKey,
        String geminiModel
) {
    public SnapshotAssistProperties {
        if (retentionDays <= 0) {
            retentionDays = 7;
        }
        if (storageRoot == null || storageRoot.isBlank()) {
            storageRoot = "/tmp/vlm-snapshot-assist";
        }
        if (serviceToken == null) {
            serviceToken = "";
        }
        if (geminiApiKey == null) {
            geminiApiKey = "";
        }
        if (geminiModel == null || geminiModel.isBlank()) {
            geminiModel = "gemini-2.5-flash";
        }
    }

    /** Effective when explicitly enabled or GEMINI_API_KEY present (unless force mock). */
    public boolean effectivelyEnabled() {
        if (forceMock && mockAnalyze) {
            return enabled;
        }
        if (enabled) {
            return true;
        }
        String key = geminiApiKey == null ? "" : geminiApiKey.trim();
        return !key.isEmpty() && !forceMock;
    }
}
