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
    void broadcastsToFacilityOverlayTopic() {
        OverlayBroadcastService service = new OverlayBroadcastService(messagingTemplate);
        OverlayMessage message = message();

        service.broadcast(message, 4L, false);

        verify(messagingTemplate).convertAndSend("/topic/facility/4/camera-overlays", message);
    }

    @Test
    void broadcastsToCompanyOverlayTopic() {
        OverlayBroadcastService service = new OverlayBroadcastService(messagingTemplate);
        OverlayMessage message = message();

        service.broadcast(message, 9L, true);

        verify(messagingTemplate).convertAndSend("/topic/company/9/camera-overlays", message);
    }

    private OverlayMessage message() {
        return new OverlayMessage(
                "1.0",
                "overlay",
                1782177631123L,
                "cam_03",
                "cam_03",
                1280,
                720,
                List.of(new OverlayEvent(
                        "FALL_DETECTED",
                        0.92,
                        7L,
                        new BoundingBox(420, 250, 180, 320))));
    }
}
