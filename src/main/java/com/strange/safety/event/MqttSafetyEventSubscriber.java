package com.strange.safety.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;
    private final AsyncEventProcessorService asyncEventProcessorService;

    public MqttSafetyEventSubscriber(
            ObjectMapper objectMapper,
            AsyncEventProcessorService asyncEventProcessorService
    ) {
        this.objectMapper = objectMapper;
        this.asyncEventProcessorService = asyncEventProcessorService;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void messageArrived(Message<?> message) {
        String topic = String.valueOf(message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
        String payload = payloadAsString(message.getPayload());
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

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
