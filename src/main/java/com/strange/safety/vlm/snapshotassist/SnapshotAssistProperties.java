package com.strange.safety.vlm.snapshotassist;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "vlm.snapshot-assist")
public record SnapshotAssistProperties(
        boolean enabled,
        String serviceToken,
        String storageRoot,
        int retentionDays,
        boolean mockAnalyze,
        String geminiApiKey
) {
    public SnapshotAssistProperties {
        if (retentionDays <= 0) {
            retentionDays = 7;
        }
        if (storageRoot == null || storageRoot.isBlank()) {
            storageRoot = "data/vlm-snapshot-assist";
        }
        if (serviceToken == null) {
            serviceToken = "";
        }
        if (geminiApiKey == null) {
            geminiApiKey = "";
        }
    }
}
