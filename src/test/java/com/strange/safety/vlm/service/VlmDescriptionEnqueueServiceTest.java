package com.strange.safety.vlm.service;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VlmDescriptionEnqueueServiceTest {

    @Mock
    private AlertEventDescriptionRepository repository;

    private VlmDescriptionEnqueueService service;

    @BeforeEach
    void setUp() {
        service = new VlmDescriptionEnqueueService(repository);
        ReflectionTestUtils.setField(service, "promptVersion", "v1");
        ReflectionTestUtils.setField(service, "vlmModelName", "mock-vlm");
        ReflectionTestUtils.setField(service, "maxRetry", 1);
        ReflectionTestUtils.setField(service, "vlmEnabled", true);
        ReflectionTestUtils.setField(service, "processExistingEvents", false);
    }

    @Test
    void disabledDoesNotClaimOrSave() {
        ReflectionTestUtils.setField(service, "vlmEnabled", false);
        AlertEvent event = sampleEventWithClip();

        service.enqueueIfMediaExists(event);

        verify(repository, never()).save(any());
        verify(repository, never()).existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                any(), any(), any(), any());
    }

    @Test
    void enabledEnqueuesWithMaxRetryOneFromConfig() {
        AlertEvent event = sampleEventWithClip();
        when(repository.existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
                any(), any(), any(), any())).thenReturn(false);
        when(repository.save(any(AlertEventDescription.class))).thenAnswer(inv -> inv.getArgument(0));

        service.enqueueIfMediaExists(event);

        ArgumentCaptor<AlertEventDescription> captor = ArgumentCaptor.forClass(AlertEventDescription.class);
        verify(repository).save(captor.capture());
        AlertEventDescription job = captor.getValue();
        assertThat(job.getMaxRetries()).isEqualTo(1);
        assertThat(job.getSourceAssetType()).isEqualTo(VlmSourceType.CLIP);
        assertThat(job.getSourceAssetKey()).contains("clips/");
        assertThat(job.getAlertEvent()).isSameAs(event);
        assertThat(event.getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void assetMissingDoesNotSave() {
        Scenario scenario = Scenario.builder().scenarioType(ScenarioType.COLLAPSE).description("c").build();
        AlertEvent event = AlertEvent.builder()
                .scenario(scenario)
                .confidenceScore(0.9f)
                .severity(AlertSeverity.WARNING)
                .detectedAt(LocalDateTime.now())
                .build();

        service.enqueueIfMediaExists(event);

        verify(repository, never()).save(any());
    }

    private static AlertEvent sampleEventWithClip() {
        Scenario scenario = Scenario.builder().scenarioType(ScenarioType.COLLAPSE).description("collapse").build();
        return AlertEvent.builder()
                .scenario(scenario)
                .confidenceScore(0.91f)
                .severity(AlertSeverity.CRITICAL)
                .clipUrl("https://bucket.s3.ap-northeast-2.amazonaws.com/clips/evt-a.mp4")
                .detectedAt(LocalDateTime.now())
                .eventId("evt-a")
                .build();
    }
}
