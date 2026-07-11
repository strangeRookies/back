package com.strange.safety.vlm.snapshotassist;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class SnapshotAssistBroadcastService {
    private static final Logger log = LoggerFactory.getLogger(SnapshotAssistBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public SnapshotAssistBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(SnapshotAssistResultMessage message) {
        // Global assist topic + optional facility-agnostic feed for dashboard mapping by eventId.
        String topic = "/topic/vlm-snapshot-assist";
        messagingTemplate.convertAndSend(topic, message);
        log.info("[SnapshotAssist] broadcast eventId={} status={} topic={}",
                message.eventId(), message.status(), topic);
    }
}
