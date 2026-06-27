package com.strange.safety.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.camera.overlay.OverlayMessage;
import com.strange.safety.camera.overlay.OverlayRelayService;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;


@Component
public class MqttSafetyEventSubscriber {

    private static final Logger log = LoggerFactory.getLogger(MqttSafetyEventSubscriber.class);

    private static final String CAMERA_STATUS_TOPIC = "safety/cameras/status";
    private static final String OVERLAY_MESSAGE_TYPE = "overlay";

    private final ObjectMapper objectMapper;
    private final AsyncEventProcessorService asyncEventProcessorService;
    private final OverlayRelayService overlayRelayService;
    private final String overlayTopic;

    public MqttSafetyEventSubscriber(
            ObjectMapper objectMapper,
            AsyncEventProcessorService asyncEventProcessorService,
            OverlayRelayService overlayRelayService,
            @Value("${mqtt.overlay-topic:camera}") String overlayTopic
    ) {
        this.objectMapper = objectMapper;
        this.asyncEventProcessorService = asyncEventProcessorService;
        this.overlayRelayService = overlayRelayService;
        this.overlayTopic = overlayTopic;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void messageArrived(Message<?> message) {
        String topic = String.valueOf(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
        String payload = payloadAsString(message.getPayload());

        if (overlayTopic.equals(topic)) {
            handleOverlayMessage(payload);
            return;
        }

        log.info("Received MQTT message from topic {}: {}", topic, payload);

        if (CAMERA_STATUS_TOPIC.equals(topic)) {
            handleCameraStatusEvent(payload);
        } else {
            handleSafetyEvent(payload);
        }
    }

    private void handleCameraStatusEvent(String payload) {
        try {
            CameraStatusEventDto event = objectMapper.readValue(payload, CameraStatusEventDto.class);
            asyncEventProcessorService.processCameraStatusEvent(event);
            if (isDisconnected(event.status())) {
                overlayRelayService.clear(event.cameraLoginId());
            }

        } catch (JsonProcessingException ex) {
            log.error("Failed to parse MQTT camera status event JSON: payload={}", payload, ex);
        } catch (RuntimeException ex) {
            log.error("Failed to process MQTT camera status event: payload={}", payload, ex);
        }
    }

    private void handleSafetyEvent(String payload) {
        try {
            SafetyEventDto event = objectMapper.readValue(payload, SafetyEventDto.class);
            asyncEventProcessorService.processEvent(event);
        } catch (Exception e) {
            log.error("[MQTT Debug] Failed to process MQTT safety event: payload={}, error={}", payload, e.getMessage(), e);
        }
    }

    private void handleOverlayMessage(String payload) {
        try {
            OverlayMessage message = objectMapper.readValue(payload, OverlayMessage.class);
            if (!OVERLAY_MESSAGE_TYPE.equals(message.messageType())) {
                log.debug("Ignoring non-overlay MQTT message on overlay topic: messageType={}", message.messageType());
                return;
            }
            int eventCount = message.events() == null ? 0 : message.events().size();
            log.info("MQTT overlay received topic={} cameraLoginId={} events={}",
                    overlayTopic, message.resolvedCameraLoginId(), eventCount);
            overlayRelayService.accept(message);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to parse MQTT overlay JSON: error={}", ex.getOriginalMessage());
        } catch (RuntimeException ex) {
            log.error("Failed to process MQTT overlay: error={}", ex.getMessage(), ex);
        }
    }

    private boolean isDisconnected(String status) {
        if (status == null) {
            return false;
        }
        return switch (status.toUpperCase()) {
            case "DISCONNECTED", "RECONNECTING", "ERROR", "DISABLED" -> true;
            default -> false;
        };
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
