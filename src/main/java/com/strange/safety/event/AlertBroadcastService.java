package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class AlertBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcastService.class);
    private static final String ALERT_TOPIC_PREFIX = "/topic/facility/";
    private static final String ALERT_TOPIC_SUFFIX = "/alerts";

    private final SimpMessagingTemplate messagingTemplate;

    public AlertBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(Long facilityId, SafetyEventDto event) {
        if (facilityId == null) {
            log.warn("FacilityId is null. Skipping broadcast for camera: {}", event.cameraId());
            return;
        }
        String topic = ALERT_TOPIC_PREFIX + facilityId + ALERT_TOPIC_SUFFIX;
        log.info("Broadcasting safety event to {}: type={}, cameraId={}, severity={}",
                topic, event.type(), event.cameraId(), event.severity());
        messagingTemplate.convertAndSend(topic, event);
    }
}
