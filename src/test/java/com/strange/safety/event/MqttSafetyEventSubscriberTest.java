package com.strange.safety.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.camera.overlay.OverlayMessage;
import com.strange.safety.camera.overlay.OverlayRelayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
class MqttSafetyEventSubscriberTest {

    @Mock
    private AsyncEventProcessorService asyncEventProcessorService;

    @Mock
    private OverlayRelayService overlayRelayService;

    @Test
    void routesCamelCaseOverlayMessageWithoutInvokingSafetyEventFlow() {
        MqttSafetyEventSubscriber subscriber = new MqttSafetyEventSubscriber(
                new ObjectMapper(),
                asyncEventProcessorService,
                overlayRelayService,
                "camera");
        String payload = """
                {
                  "schemaVersion": "1.0",
                  "messageType": "overlay",
                  "timestampMs": 1782177631123,
                  "streamId": "cam_03",
                  "frameWidth": 1280,
                  "frameHeight": 720,
                  "events": [{
                    "type": "FALL_DETECTED",
                    "confidence": 0.92,
                    "trackingId": 7,
                    "boundingBox": {"x": 420, "y": 250, "width": 180, "height": 320}
                  }]
                }
                """;

        subscriber.messageArrived(MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.RECEIVED_TOPIC, "camera")
                .build());

        ArgumentCaptor<OverlayMessage> captor = ArgumentCaptor.forClass(OverlayMessage.class);
        verify(overlayRelayService).accept(captor.capture());
        verify(asyncEventProcessorService, never()).processEvent(any());
        OverlayMessage message = captor.getValue();
        assertThat(message.streamId()).isEqualTo("cam_03");
        assertThat(message.events().getFirst().trackingId()).isEqualTo(7L);
        assertThat(message.events().getFirst().boundingBox().width()).isEqualTo(180);
    }
}
