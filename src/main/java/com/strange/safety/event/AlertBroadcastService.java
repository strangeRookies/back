package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AlertBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(Long targetId, boolean isCorporate, SafetyEventDto event) {
        if (targetId == null) {
            log.warn("TargetId is null. Skipping broadcast for camera: {}", event.cameraId());
            return;
        }
        String prefix = isCorporate ? "/topic/company/" : "/topic/facility/";
        String topic = prefix + targetId + "/alerts";
        log.info("Broadcasting safety event to {}: type={}, cameraId={}, severity={}",
                topic, event.type(), event.cameraId(), event.severity());
        messagingTemplate.convertAndSend(topic, event);
    }
}
