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
    private final com.strange.safety.camera.repository.CameraRepository cameraRepository;

    @Async("eventProcessingExecutor")
    public void processEvent(SafetyEventDto event) {
        Long facilityId = null;
        try {
            String cameraLoginId = event.cameraLoginId() != null ? event.cameraLoginId() : event.cameraId();
            facilityId = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc(cameraLoginId)
                    .map(camera -> camera.getFacility().getId())
                    .orElse(null);

            log.info("[MQTT Async] Parsed event. type={}, cameraId={}, facilityId={}", 
                    event.type(), event.cameraId(), facilityId);
            alertBroadcastService.broadcast(facilityId, event);
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

            Long facilityId = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc(event.cameraLoginId())
                    .map(camera -> camera.getFacility().getId())
                    .orElse(null);
            
            cameraStatusBroadcastService.broadcast(facilityId, event);
        } catch (RuntimeException ex) {
            log.error("Failed to process camera status event asynchronously: cameraLoginId={}, status={}, error={}",
                    event.cameraLoginId(), event.status(), ex.getMessage(), ex);
        }
    }
}
