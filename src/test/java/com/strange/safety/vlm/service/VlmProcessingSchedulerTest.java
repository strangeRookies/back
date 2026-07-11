package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import com.strange.safety.alert.service.S3Service;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.vlm.embedding.PgVectorProjectionWriter;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VlmProcessingSchedulerTest {

    @Mock
    private AlertEventDescriptionRepository repository;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private PgVectorProjectionWriter pgVectorProjectionWriter;
    @Mock
    private S3Service s3Service;

    private VlmProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new VlmProcessingScheduler(
                repository,
                embeddingService,
                pgVectorProjectionWriter,
                s3Service,
                new ObjectMapper()
        );
        ReflectionTestUtils.setField(scheduler, "mockMode", true);
        ReflectionTestUtils.setField(scheduler, "pythonExecutable", "python");
        ReflectionTestUtils.setField(scheduler, "processScript", "missing-process_vlm.py");
        ReflectionTestUtils.setField(scheduler, "batchSize", 2);
        ReflectionTestUtils.setField(scheduler, "timeoutSeconds", 1L);
        ReflectionTestUtils.setField(scheduler, "dailyJobLimit", 10);
        ReflectionTestUtils.setField(scheduler, "maxRetry", 1);
        ReflectionTestUtils.setField(scheduler, "processExistingEvents", false);
    }

    @Test
    void schedulerBeanIsGatedByVlmEnabledProperty() {
        ConditionalOnProperty cond = VlmProcessingScheduler.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(cond).isNotNull();
        assertThat(cond.name()).contains("vlm.enabled");
        assertThat(cond.havingValue()).isEqualTo("true");
        // havingValue=true and no matchIfMissing → bean absent when vlm.enabled=false (default)
    }

    @Test
    void mockModeJobSucceedsWithoutTouchingAlertEventStatus() {
        when(embeddingService.embed(anyString())).thenReturn(new double[]{0.1, 0.2, 0.3});
        when(embeddingService.encode(any())).thenReturn("0.1,0.2,0.3");
        when(embeddingService.embeddingModelName()).thenReturn("mock-embed");

        AlertEvent event = sampleEvent();
        AlertStatus before = event.getStatus();
        AlertEventDescription job = sampleJob(event, 1);

        scheduler.process(job);

        assertThat(job.getStatus()).isEqualTo(VlmJobStatus.SUCCESS);
        assertThat(job.isMockResult()).isTrue();
        assertThat(job.getVlmDescription()).contains("Mock VLM");
        assertThat(event.getStatus()).isEqualTo(before);
        assertThat(event.getStatus()).isEqualTo(AlertStatus.PENDING);
        verify(pgVectorProjectionWriter).projectIfEnabled(any(), any());
    }

    @Test
    void missingScriptFailsJobOnly(@TempDir Path tempDir) {
        ReflectionTestUtils.setField(scheduler, "mockMode", false);
        Path missing = tempDir.resolve("not-there.py");
        ReflectionTestUtils.setField(scheduler, "processScript", missing.toString());
        when(embeddingService.canEmbed()).thenReturn(true);

        AlertEvent event = sampleEvent();
        AlertStatus before = event.getStatus();
        AlertEventDescription job = sampleJob(event, 0);

        scheduler.process(job);

        assertThat(job.getStatus()).isEqualTo(VlmJobStatus.FAILED);
        assertThat(job.getErrorMessage()).contains("VLM process script not found");
        assertThat(event.getStatus()).isEqualTo(before);
        verify(s3Service, never()).generatePresignedUrl(anyString());
    }

    @Test
    void processTimeoutMessageIsolatesToJob() {
        // Drive pure timeout helper used by markProcessSuccess
        String msg = VlmProcessSupport.timeoutMessage(false, 120).orElseThrow();
        AlertEvent event = sampleEvent();
        AlertStatus before = event.getStatus();
        AlertEventDescription job = sampleJob(event, 1);

        job.markFailed(msg);

        assertThat(job.getStatus()).isEqualTo(VlmJobStatus.PENDING);
        assertThat(job.getErrorMessage()).contains("timed out after 120");
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(before);
    }

    @Test
    void maxRetryOneMarksFailedAfterTwoFailures() {
        AlertEvent event = sampleEvent();
        AlertEventDescription job = sampleJob(event, 1);

        job.markFailed("first failure");

        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(VlmJobStatus.PENDING);
        assertThat(VlmProcessSupport.hasRetryBudget(job.getRetryCount(), job.getMaxRetries())).isTrue();

        job.markFailed("second failure");

        assertThat(job.getRetryCount()).isEqualTo(2);
        assertThat(job.getStatus()).isEqualTo(VlmJobStatus.FAILED);
        assertThat(VlmProcessSupport.hasRetryBudget(job.getRetryCount(), job.getMaxRetries())).isFalse();
        assertThat(event.getStatus()).isEqualTo(AlertStatus.PENDING);
    }

    @Test
    void effectiveMaxRetryUsesJobThenConfig() {
        AlertEventDescription job = sampleJob(sampleEvent(), 1);
        assertThat(scheduler.effectiveMaxRetry(job)).isEqualTo(1);
        ReflectionTestUtils.setField(scheduler, "maxRetry", 1);
        AlertEventDescription zero = sampleJob(sampleEvent(), 0);
        // builder still sets 0; effective falls back to scheduler maxRetry
        assertThat(scheduler.effectiveMaxRetry(zero)).isEqualTo(1);
    }

    @Test
    void sanitizeLogRedactsApiKeysAndTokens() {
        String raw = "failed auth api_key=secret123 Authorization: Bearer abc TOKEN=xyz";
        String cleaned = VlmProcessingScheduler.sanitizeLog(raw);
        assertThat(cleaned).doesNotContain("secret123");
        assertThat(cleaned).doesNotContain("Bearer abc");
        assertThat(cleaned).contains("***");
    }

    @Test
    void sanitizeLogTruncatesLongMessages() {
        String raw = "x".repeat(5000);
        String cleaned = VlmProcessingScheduler.sanitizeLog(raw);
        assertThat(cleaned.length()).isLessThan(2100);
        assertThat(cleaned).endsWith("...");
    }

    @Test
    void scriptGateUsedForPresentFile(@TempDir Path tempDir) throws Exception {
        Path script = tempDir.resolve("process_vlm.py");
        Files.writeString(script, "# stub\n");
        assertThat(VlmProcessSupport.scriptMissingMessage(script)).isEmpty();
        ReflectionTestUtils.setField(scheduler, "processScript", script.toString());
        ReflectionTestUtils.setField(scheduler, "mockMode", false);
        when(embeddingService.canEmbed()).thenReturn(true);
        when(s3Service.generatePresignedUrl(anyString())).thenReturn("https://example.com/in");
        when(s3Service.generatePresignedPutUrl(anyString(), anyString(), any()))
                .thenReturn("https://example.com/out");

        // Real process will fail quickly (not valid python VLM) — still must not touch AlertEvent
        AlertEvent event = sampleEvent();
        AlertStatus before = event.getStatus();
        AlertEventDescription job = sampleJob(event, 1);
        scheduler.process(job);

        assertThat(job.getStatus()).isIn(VlmJobStatus.FAILED, VlmJobStatus.PENDING, VlmJobStatus.SUCCESS);
        assertThat(event.getStatus()).isEqualTo(before);
    }

    private static AlertEvent sampleEvent() {
        Scenario scenario = Scenario.builder()
                .scenarioType(ScenarioType.COLLAPSE)
                .description("collapse")
                .build();
        return AlertEvent.builder()
                .scenario(scenario)
                .confidenceScore(0.9f)
                .severity(AlertSeverity.CRITICAL)
                .clipUrl("https://bucket.s3.amazonaws.com/clips/x.mp4")
                .detectedAt(LocalDateTime.now())
                .eventId("e-1")
                .build();
    }

    private static AlertEventDescription sampleJob(AlertEvent event, int maxRetries) {
        return AlertEventDescription.builder()
                .alertEvent(event)
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clips/x.mp4")
                .promptVersion("v1")
                .vlmModelName("mock-vlm")
                .maxRetries(maxRetries)
                .build();
    }
}
