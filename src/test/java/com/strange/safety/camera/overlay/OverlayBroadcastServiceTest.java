package com.strange.safety.camera.overlay;

import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@ExtendWith(MockitoExtension.class)
class OverlayBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Test
    void broadcastsToCameraOverlayTopic() {
        OverlayBroadcastService service = new OverlayBroadcastService(messagingTemplate);
        OverlayMessage message = new OverlayMessage(
                "1.0",
                "overlay",
                1782177631123L,
                "cam_03",
                1280,
                720,
                List.of(new OverlayEvent(
                        "FALL_DETECTED",
                        0.92,
                        7L,
                        new BoundingBox(420, 250, 180, 320))));

        service.broadcast(message);

        verify(messagingTemplate).convertAndSend("/topic/cameras/cam_03/overlay", message);
    }
}
