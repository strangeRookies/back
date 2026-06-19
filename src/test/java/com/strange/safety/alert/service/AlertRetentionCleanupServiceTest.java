package com.strange.safety.alert.service;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.alert.repository.AlertEventRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AlertRetentionCleanupServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Test
    void deleteExpiredAlertsDeletesEventsOlderThanRetentionDays() {
        AlertRetentionCleanupService service = new AlertRetentionCleanupService(alertEventRepository);
        ReflectionTestUtils.setField(service, "retentionDays", 30L);
        when(alertEventRepository.deleteByDetectedAtBefore(org.mockito.ArgumentMatchers.any())).thenReturn(2L);

        service.deleteExpiredAlerts();

        verify(alertEventRepository).deleteByDetectedAtBefore(argThat(cutoff ->
                cutoff.isAfter(LocalDateTime.now().minusDays(31))
                        && cutoff.isBefore(LocalDateTime.now().minusDays(29))));
    }
}
