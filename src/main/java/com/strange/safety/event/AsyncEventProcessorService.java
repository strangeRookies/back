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
    private final com.strange.safety.corporatecamera.repository.CorporateCameraRepository corporateCameraRepository;

    @Async("eventProcessingExecutor")
    public void processEvent(SafetyEventDto event) {
        Long targetId = null;
        boolean isCorporate = false;
        try {
            String cameraLoginId = event.cameraLoginId() != null ? event.cameraLoginId() : event.cameraId();
            targetId = cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(cameraLoginId, com.strange.safety.camera.entity.CameraStatus.ACTIVE)
                    .map(camera -> camera.getFacility().getId())
                    .orElse(null);

            if (targetId == null) {
                targetId = corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(cameraLoginId, com.strange.safety.camera.entity.CameraStatus.ACTIVE)
                        .map(corp -> corp.getCompanyProfile().getId())
                        .orElse(null);
                if (targetId != null) {
                    isCorporate = true;
                }
            }

            log.info("[MQTT Async] Parsed event. type={}, cameraId={}, targetId={}, isCorporate={}", 
                    event.type(), event.cameraId(), targetId, isCorporate);
            alertBroadcastService.broadcast(targetId, isCorporate, event);
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

            Long targetId = cameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(event.cameraLoginId(), com.strange.safety.camera.entity.CameraStatus.ACTIVE)
                    .map(camera -> camera.getFacility().getId())
                    .orElse(null);
            
            boolean isCorporate = false;
            if (targetId == null) {
                targetId = corporateCameraRepository.findFirstByCameraLoginIdAndStatusOrderByIdDesc(event.cameraLoginId(), com.strange.safety.camera.entity.CameraStatus.ACTIVE)
                        .map(corp -> corp.getCompanyProfile().getId())
                        .orElse(null);
                if (targetId != null) {
                    isCorporate = true;
                }
            }
            
            cameraStatusBroadcastService.broadcast(targetId, isCorporate, event);
        } catch (RuntimeException ex) {
            log.error("Failed to process camera status event asynchronously: cameraLoginId={}, status={}, error={}",
                    event.cameraLoginId(), event.status(), ex.getMessage(), ex);
        }
    }
}
