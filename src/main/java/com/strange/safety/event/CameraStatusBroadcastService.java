package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * AI 서버로부터 수신한 카메라 연결 상태 이벤트를
 * WebSocket /topic/camera-status 경로로 프론트엔드에 브로드캐스트하는 서비스.
 */
@Service
public class CameraStatusBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(CameraStatusBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public CameraStatusBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(Long targetId, boolean isCorporate, CameraStatusEventDto event) {
        if (targetId == null) {
            log.warn("TargetId is null. Skipping camera status broadcast for: {}", event.cameraLoginId());
            return;
        }
        String prefix = isCorporate ? "/topic/company/" : "/topic/facility/";
        String topic = prefix + targetId + "/camera-status";
        log.info("Broadcasting camera status event to {}: cameraLoginId={}, status={}, reason={}",
                topic, event.cameraLoginId(), event.status(), event.reason());
        messagingTemplate.convertAndSend(topic, event);
    }
}
