package com.strange.safety.camera.overlay;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OverlayBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(OverlayBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(OverlayMessage message, Long targetId, boolean corporate) {
        String destination = corporate
                ? "/topic/company/" + targetId + "/camera-overlays"
                : "/topic/facility/" + targetId + "/camera-overlays";
        messagingTemplate.convertAndSend(destination, message);
        log.debug("Overlay STOMP published destination={}", destination);
    }
}
