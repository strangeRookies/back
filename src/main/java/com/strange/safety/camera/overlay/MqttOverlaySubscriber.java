package com.strange.safety.camera.overlay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MqttOverlaySubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttOverlaySubscriber.class);
    private static final String OVERLAY_MESSAGE_TYPE = "overlay";

    private final ObjectMapper objectMapper;
    private final OverlayRelayService overlayRelayService;
    private final String overlayTopic;

    public MqttOverlaySubscriber(
            ObjectMapper objectMapper,
            OverlayRelayService overlayRelayService,
            @Value("${mqtt.overlay-topic:camera}") String overlayTopic
    ) {
        this.objectMapper = objectMapper;
        this.overlayRelayService = overlayRelayService;
        this.overlayTopic = overlayTopic;
    }

    @ServiceActivator(inputChannel = "mqttOverlayInputChannel")
    public void messageArrived(Message<?> message) {
        String payload = payloadAsString(message.getPayload());
        try {
            OverlayMessage overlayMessage = objectMapper.readValue(payload, OverlayMessage.class);
            if (!OVERLAY_MESSAGE_TYPE.equals(overlayMessage.messageType())
                    && !"frame_sync".equals(overlayMessage.messageType())) {
                log.debug("Ignoring non-overlay MQTT message on overlay topic: messageType={}",
                        overlayMessage.messageType());
                return;
            }
            int eventCount = overlayMessage.events() == null ? 0 : overlayMessage.events().size();
            log.debug("MQTT overlay received topic={} cameraLoginId={} events={}",
                    overlayTopic, overlayMessage.resolvedCameraLoginId(), eventCount);
            overlayRelayService.accept(overlayMessage);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse MQTT overlay JSON: error={}", ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to process MQTT overlay: error={}", ex.getMessage(), ex);
        }
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
