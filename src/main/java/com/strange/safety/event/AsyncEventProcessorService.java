package com.strange.safety.event;

import com.strange.safety.alert.service.AlertEventService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AsyncEventProcessorService {

    private static final Logger log = LoggerFactory.getLogger(AsyncEventProcessorService.class);

    private final AlertEventService alertEventService;
    private final AlertBroadcastService alertBroadcastService;
    private final FcmService fcmService;
    private final com.strange.safety.camera.service.CameraStatusService cameraStatusService;
    private final CameraStatusBroadcastService cameraStatusBroadcastService;

    @Async("eventProcessingExecutor")
    public void processEvent(SafetyEventDto event) {
        try {
            log.info("[MQTT Async] Parsed event. type={}, cameraId={}", event.type(), event.cameraId());
            alertBroadcastService.broadcast(event);
            fcmService.sendAlertNotification(event);
        } catch (RuntimeException ex) {
            log.error("Failed to broadcast safety event asynchronously: cameraId={}, type={}, error={}",
                    event.cameraId(), event.type(), ex.getMessage(), ex);
        }

        try {
            alertEventService.createEvent(event);
        } catch (RuntimeException ex) {
            log.error("Failed to persist safety event asynchronously: cameraId={}, type={}, error={}",
                    event.cameraId(), event.type(), ex.getMessage(), ex);
        }
    }

    @Async("eventProcessingExecutor")
    public void processCameraStatusEvent(CameraStatusEventDto event) {
        try {
            log.info("[MQTT Async] Camera status event received: cameraLoginId={}, status={}, reason={}",
                    event.cameraLoginId(), event.status(), event.reason());
            cameraStatusService.applyStatusEvent(event);
            cameraStatusBroadcastService.broadcast(event);
        } catch (RuntimeException ex) {
            log.error("Failed to process camera status event asynchronously: cameraLoginId={}, status={}, error={}",
                    event.cameraLoginId(), event.status(), ex.getMessage(), ex);
        }
    }
}
