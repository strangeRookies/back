package com.strange.safety.alert.service;

import com.strange.safety.alert.repository.AlertEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlertRetentionCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AlertRetentionCleanupService.class);

    private final AlertEventRepository alertEventRepository;

    @Value("${alerts.retention-days:30}")
    private long retentionDays;

    @Transactional
    @Scheduled(cron = "${alerts.cleanup-cron:0 0 3 * * *}")
    public void deleteExpiredAlerts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        long deletedCount = alertEventRepository.deleteByDetectedAtBefore(cutoff);
        if (deletedCount > 0) {
            log.info("Deleted expired alert events: count={}, cutoff={}", deletedCount, cutoff);
        }
    }
}
