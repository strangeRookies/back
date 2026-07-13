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
        if (event.isEvidenceEvent()) {
            log.info("[MQTT Async] Evidence event received. Skipping STOMP/FCM and persisting only: eventId={}, cameraId={}, cameraLoginId={}, clipUrl={}",
                    event.eventId(), event.cameraId(), event.cameraLoginId(), event.clipUrl());
            persistEvent(event);
            return;
        }

        if (!alertEventService.isSupportedEventType(event.type())) {
            log.warn("Skipping unsupported AI safety event type: eventId={}, type={}", event.eventId(), event.type());
            return;
        }

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

            log.info("[MQTT Async] Parsed event. type={}, eventPhase={}, cameraId={}, targetId={}, isCorporate={}",
                    event.type(), event.eventPhase(), event.cameraId(), targetId, isCorporate);
            if (alertEventService.isAlreadyNotified(event.eventId())) {
                log.info("[MQTT Async] eventId={} already has an alert row; skipping duplicate broadcast/FCM (clip re-publish).",
                        event.eventId());
            } else {
                alertBroadcastService.broadcast(targetId, isCorporate, event);
                fcmService.sendAlertNotification(event);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to broadcast safety event asynchronously: cameraId={}, type={}, error={}",
                    event.cameraId(), event.type(), ex.getMessage(), ex);
        }

        persistEvent(event);
    }

    private void persistEvent(SafetyEventDto event) {
        try {
            alertEventService.createEvent(event);
        } catch (RuntimeException ex) {
            log.error("Failed to persist safety event asynchronously: cameraId={}, type={}, eventPhase={}, error={}",
                    event.cameraId(), event.type(), event.eventPhase(), ex.getMessage(), ex);
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
