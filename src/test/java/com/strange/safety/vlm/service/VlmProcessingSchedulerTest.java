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
import com.strange.safety.vlm.repository.PgVectorSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VlmProcessingSchedulerTest {
    private PgVectorSearchRepository pgVectorRepository;
    private VlmProcessingScheduler scheduler;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        pgVectorRepository = mock(PgVectorSearchRepository.class);
        scheduler = new VlmProcessingScheduler(
                mock(AlertEventDescriptionRepository.class),
                mock(S3Service.class), mapper, new VlmIndexPayloadParser(mapper), pgVectorRepository);
        ReflectionTestUtils.setField(scheduler, "mockMode", true);
        ReflectionTestUtils.setField(scheduler, "captureZoneId", "Asia/Seoul");
        ReflectionTestUtils.setField(scheduler, "configuredClipStartSec", "0.5");
        ReflectionTestUtils.setField(scheduler, "configuredClipEndSec", "8.5");
    }

    @Test
    void storesContractDocumentAndEmbeddingWithoutBackendReembedding() {
        ReflectionTestUtils.setField(scheduler, "pgVectorEnabled", false);
        AlertEventDescription job = job(1);

        ReflectionTestUtils.invokeMethod(scheduler, "process", job);

        assertEquals(VlmJobStatus.SUCCESS, job.getStatus());
        assertEquals("Mock VLM safety event: 작업자 쓰러짐 바닥 안전모 복도", job.getVlmDescription());
        assertEquals(768, job.getDescriptionEmbedding().split(",").length);
        assertEquals("mock-vlm-index-768", job.getEmbeddingModelName());
        assertEquals("", job.getDeidentifiedKeyframeKeys());
    }

    @Test
    void projectionFailureCannotLeaveSearchableSuccess() {
        ReflectionTestUtils.setField(scheduler, "pgVectorEnabled", true);
        doThrow(new IllegalStateException("projection unavailable"))
                .when(pgVectorRepository).project(anyLong(), anyString());
        AlertEventDescription job = job(1);

        ReflectionTestUtils.invokeMethod(scheduler, "process", job);

        assertEquals(VlmJobStatus.FAILED, job.getStatus());
        assertNull(job.getDescriptionEmbedding());
        assertFalse(job.isMockResult());
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
