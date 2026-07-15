package com.strange.safety.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.alert.service.AlertEventService;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.camera.service.CameraStatusService;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.scenario.entity.ScenarioType;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AsyncEventProcessorServiceTest {

    @Mock
    private AlertEventService alertEventService;

    @Mock
    private AlertBroadcastService alertBroadcastService;

    @Mock
    private FcmService fcmService;

    @Mock
    private CameraStatusService cameraStatusService;

    @Mock
    private CameraStatusBroadcastService cameraStatusBroadcastService;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private CorporateCameraRepository corporateCameraRepository;

    private AsyncEventProcessorService service;

    @BeforeEach
    void setUp() {
        service = new AsyncEventProcessorService(
                alertEventService,
                alertBroadcastService,
                fcmService,
                cameraStatusService,
                cameraStatusBroadcastService,
                cameraRepository,
                corporateCameraRepository
        );
    }

    @Test
    void realtimeEventBroadcastsFcmAndPersists() {
        SafetyEventDto event = event("realtime", null);
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(alertEventService.isSupportedEventType("faint")).thenReturn(true);
        when(alertEventService.resolveScenarioType("faint")).thenReturn(ScenarioType.COLLAPSE);
        when(alertEventService.getDisplayMessage(ScenarioType.COLLAPSE)).thenReturn("쓰러짐 감지");
        when(alertEventService.isAlreadyNotified("evt-1")).thenReturn(false);

        service.processEvent(event);

        verify(alertBroadcastService).broadcast(null, false, event, ScenarioType.COLLAPSE, "쓰러짐 감지");
        verify(fcmService).sendAlertNotification(event);
        verify(alertEventService).createEvent(event);
    }

    @Test
    void evidenceEventPersistsWithoutBroadcastOrFcm() {
        SafetyEventDto event = event("evidence", "https://example.com/clip.mp4");

        service.processEvent(event);

        verify(alertBroadcastService, never()).broadcast(any(), anyBoolean(), any(), any(), any());
        verify(fcmService, never()).sendAlertNotification(any());
        verify(alertEventService, never()).isAlreadyNotified(any());
        verify(cameraRepository, never()).findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any());
        verify(corporateCameraRepository, never()).findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any());
        verify(alertEventService).createEvent(event);
    }

    @Test
    void alreadyPersistedRealtimeEventSkipsBroadcastAndFcmButStillPersistsForDedupHandling() {
        SafetyEventDto event = event("realtime", null);
        when(cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(alertEventService.isSupportedEventType("faint")).thenReturn(true);
        when(alertEventService.resolveScenarioType("faint")).thenReturn(ScenarioType.COLLAPSE);
        when(alertEventService.getDisplayMessage(ScenarioType.COLLAPSE)).thenReturn("쓰러짐 감지");
        when(alertEventService.isAlreadyNotified("evt-1")).thenReturn(true);

        service.processEvent(event);

        verify(alertBroadcastService, never()).broadcast(any(), anyBoolean(), any(), any(), any());
        verify(fcmService, never()).sendAlertNotification(any());
        verify(alertEventService).createEvent(event);
    }

    @Test
    void unsupportedRealtimeEventSkipsBroadcastFcmAndPersistence() {
        SafetyEventDto event = event("realtime", null);
        when(alertEventService.isSupportedEventType("faint")).thenReturn(false);

        service.processEvent(event);

        verify(alertBroadcastService, never()).broadcast(any(), anyBoolean(), any(), any(), any());
        verify(fcmService, never()).sendAlertNotification(any());
        verify(alertEventService, never()).createEvent(any());
        verify(cameraRepository, never()).findFirstByCameraLoginIdAndStatusOrderByIdDesc(any(), any());
    }

    private SafetyEventDto event(String eventPhase, String clipUrl) {
        return new SafetyEventDto(
                "event",
                eventPhase,
                "frame-1",
                "faint",
                null,
                "cam_05",
                "evt-1",
                "2026-07-09T00:00:00Z",
                "HIGH",
                "AI safety event detected",
                null,
                0.9f,
                null,
                null,
                "track-1",
                null,
                null,
                clipUrl,
                1783410059000L,
                1783410062400L,
                1783410062500L,
                1783410062500L,
                null,
                1783410062600L
        );
    }
}
