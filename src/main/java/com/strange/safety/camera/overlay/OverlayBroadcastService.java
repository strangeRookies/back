package com.strange.safety.camera.overlay;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OverlayBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcast(OverlayMessage message) {
        messagingTemplate.convertAndSend(
                "/topic/cameras/" + message.streamId() + "/overlay",
                message);
    }
}
