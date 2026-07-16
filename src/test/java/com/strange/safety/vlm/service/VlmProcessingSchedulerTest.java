package com.strange.safety.vlm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.service.S3Service;
import com.strange.safety.camera.entity.Camera;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.scenario.entity.ScenarioType;
import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.entity.VlmSourceType;
import com.strange.safety.vlm.repository.AlertEventDescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
class VlmProcessingSchedulerTest {
    private AlertEventDescriptionRepository repository;
    private VlmClipJobCompletionService clipJobCompletionService;
    private VlmProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        repository = mock(AlertEventDescriptionRepository.class);
        clipJobCompletionService = mock(VlmClipJobCompletionService.class);
        scheduler = new VlmProcessingScheduler(
                repository,
                mock(S3Service.class),
                mapper,
                new VlmIndexPayloadParser(mapper),
                mock(VlmClipJobClaimService.class),
                clipJobCompletionService);
        ReflectionTestUtils.setField(scheduler, "mockMode", true);
        ReflectionTestUtils.setField(scheduler, "captureZoneId", "Asia/Seoul");
        ReflectionTestUtils.setField(scheduler, "configuredClipStartSec", "");
        ReflectionTestUtils.setField(scheduler, "configuredClipEndSec", "8.5");
    }

    @Test
    void metadataOmitsClipEndWhenNotConfigured() throws Exception {
        ReflectionTestUtils.setField(scheduler, "configuredClipEndSec", "");
        AlertEventDescription job = job(1);
        Object metadata = ReflectionTestUtils.invokeMethod(
                scheduler,
                "metadataJson",
                job,
                ReflectionTestUtils.invokeMethod(scheduler, "requestContext", job));
        assertFalse(((String) metadata).contains("clip_end_sec"));
    }

    @Test
    void metadataIncludesClipStartWhenStartDefaultsToZero() throws Exception {
        ReflectionTestUtils.setField(scheduler, "configuredClipEndSec", "");
        AlertEventDescription job = job(1);
        String metadata = (String) ReflectionTestUtils.invokeMethod(
                scheduler,
                "metadataJson",
                job,
                ReflectionTestUtils.invokeMethod(scheduler, "requestContext", job));
        assertFalse(metadata.contains("clip_end_sec"));
        assertTrue(metadata.contains("\"clip_start_sec\":0.0"));
    }

    @Test
    void rejectsInvalidClipBoundsWhenEndConfigured() {
        ReflectionTestUtils.setField(scheduler, "configuredClipStartSec", "2.0");
        ReflectionTestUtils.setField(scheduler, "configuredClipEndSec", "1.0");
        AlertEventDescription job = job(1);
        Exception thrown = assertThrows(Exception.class,
                () -> ReflectionTestUtils.invokeMethod(scheduler, "requestContext", job));
        Throwable cause = thrown;
        while (cause.getCause() != null && !(cause instanceof IllegalStateException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(IllegalStateException.class, cause);
        assertEquals("Configured VLM clip bounds are invalid", cause.getMessage());
    }

    @Test
    void storesContractDocumentAndEmbeddingWithoutBackendReembedding() throws Exception {
        AlertEventDescription job = job(1);
        when(repository.findById(91L)).thenReturn(Optional.of(job));

        ReflectionTestUtils.invokeMethod(scheduler, "processClaimedJob", 91L);

        verify(clipJobCompletionService).markSuccess(eq(91L), any());
    }

    @Test
    void projectionFailureCannotLeaveSearchableSuccess() throws Exception {
        AlertEventDescription job = job(1);
        when(repository.findById(91L)).thenReturn(Optional.of(job));
        doThrow(new IOException("projection unavailable"))
                .when(clipJobCompletionService).markSuccess(eq(91L), any());

        ReflectionTestUtils.invokeMethod(scheduler, "processClaimedJob", 91L);

        verify(clipJobCompletionService).markFailed(eq(91L), any());
    }

    private AlertEventDescription job(int maxRetries) {
        AlertEvent event = mock(AlertEvent.class);
        Camera camera = mock(Camera.class);
        Scenario scenario = mock(Scenario.class);
        when(event.getId()).thenReturn(41L);
        when(event.getEventId()).thenReturn("incident-41");
        when(event.getCamera()).thenReturn(camera);
        when(camera.getCameraLoginId()).thenReturn("camera-41");
        when(event.getDetectedAt()).thenReturn(LocalDateTime.of(2026, 7, 15, 12, 30));
        when(event.getSeverity()).thenReturn(AlertSeverity.CRITICAL);
        when(event.getScenario()).thenReturn(scenario);
        when(scenario.getScenarioType()).thenReturn(ScenarioType.COLLAPSE);
        AlertEventDescription job = AlertEventDescription.builder()
                .alertEvent(event)
                .sourceAssetType(VlmSourceType.CLIP)
                .sourceAssetKey("clips/incident-41.mp4")
                .promptVersion("v1")
                .vlmModelName("mock")
                .maxRetries(maxRetries)
                .build();
        ReflectionTestUtils.setField(job, "id", 91L);
        job.markProcessing(LocalDateTime.now().plusMinutes(1));
        return job;
    }
}
