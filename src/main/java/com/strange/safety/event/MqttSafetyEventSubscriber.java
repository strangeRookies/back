package com.strange.safety.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class MqttSafetyEventSubscriber implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttSafetyEventSubscriber.class);

    private final ObjectMapper objectMapper;
    private final AlertBroadcastService alertBroadcastService;
    private final MqttConnectOptions connectOptions;
    private final String brokerUrl;
    private final String clientId;
    private final String topic;
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private MqttClient mqttClient;

    public MqttSafetyEventSubscriber(
            ObjectMapper objectMapper,
            AlertBroadcastService alertBroadcastService,
            MqttConnectOptions connectOptions,
            @Value("${mqtt.broker-url:tcp://localhost:1883}") String brokerUrl,
            @Value("${mqtt.client-id:safety-backend}") String clientId,
            @Value("${mqtt.topic:safety/events}") String topic
    ) {
        this.objectMapper = objectMapper;
        this.alertBroadcastService = alertBroadcastService;
        this.connectOptions = connectOptions;
        this.brokerUrl = brokerUrl;
        this.clientId = clientId;
        this.topic = topic;
    }

    @Scheduled(
            fixedDelayString = "${mqtt.reconnect-delay-ms:5000}",
            initialDelayString = "${mqtt.initial-connect-delay-ms:1000}"
    )
    public void ensureConnected() {
        if (isConnected() || !connecting.compareAndSet(false, true)) {
            return;
        }

        try {
            MqttClient client = getOrCreateClient();
            if (!client.isConnected()) {
                log.info("Connecting to MQTT broker: brokerUrl={}, clientId={}, topic={}", brokerUrl, clientId, topic);
                client.connect(connectOptions);
                subscribeToSafetyEvents(client);
            }
        } catch (MqttException ex) {
            log.warn("MQTT connection failed: brokerUrl={}, clientId={}, topic={}, error={}",
                    brokerUrl, clientId, topic, ex.getMessage());
        } finally {
            connecting.set(false);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT connection established: serverURI={}, reconnect={}", serverURI, reconnect);
        try {
            subscribeToSafetyEvents(getOrCreateClient());
        } catch (MqttException ex) {
            log.warn("MQTT subscribe failed after connect: topic={}, error={}", topic, ex.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        String message = cause == null ? "unknown" : cause.getMessage();
        log.warn("MQTT connection lost: brokerUrl={}, error={}", brokerUrl, message);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        log.info("Received MQTT safety event from topic {}: {}", topic, payload);

        try {
            SafetyEventDto event = objectMapper.readValue(payload, SafetyEventDto.class);
            alertBroadcastService.broadcast(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse MQTT safety event JSON: payload={}", payload, ex);
        } catch (RuntimeException ex) {
            log.error("Failed to broadcast MQTT safety event: payload={}", payload, ex);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // This service only subscribes to MQTT messages.
    }

    @PreDestroy
    public void shutdown() {
        if (mqttClient == null) {
            return;
        }

        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
        } catch (MqttException ex) {
            log.warn("MQTT disconnect failed: error={}", ex.getMessage());
        }
    }

    private synchronized MqttClient getOrCreateClient() throws MqttException {
        if (mqttClient == null) {
            mqttClient = new MqttClient(brokerUrl, clientId, new MemoryPersistence());
            mqttClient.setCallback(this);
        }
        return mqttClient;
    }

    private boolean isConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    private void subscribeToSafetyEvents(MqttClient client) throws MqttException {
        if (!client.isConnected()) {
            return;
        }
        client.subscribe(topic, 0);
        log.info("Subscribed to MQTT topic: {}", topic);
    }
}
