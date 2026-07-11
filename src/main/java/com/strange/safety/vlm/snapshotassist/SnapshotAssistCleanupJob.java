package com.strange.safety.vlm.snapshotassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class SnapshotAssistCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(SnapshotAssistCleanupJob.class);

    private final SnapshotAssistProperties properties;
    private final SnapshotAssistStore store;

    public SnapshotAssistCleanupJob(SnapshotAssistProperties properties, SnapshotAssistStore store) {
        this.properties = properties;
        this.store = store;
    }

    @Scheduled(cron = "${vlm.snapshot-assist.cleanup-cron:0 15 3 * * *}")
    public void cleanup() {
        if (!properties.enabled()) {
            return;
        }
        try {
            Instant cutoff = Instant.now().minus(properties.retentionDays(), ChronoUnit.DAYS);
            int removed = store.deleteOlderThan(cutoff);
            if (removed > 0) {
                log.info("[SnapshotAssist] cleaned {} expired snapshot dirs (retentionDays={})",
                        removed, properties.retentionDays());
            }
        } catch (Exception ex) {
            log.warn("[SnapshotAssist] cleanup failed: {}", ex.getMessage());
        }
    }
}
