package com.strange.safety.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class MqttConfigTest {

    private MqttConfig mqttConfig;

    @BeforeEach
    void setUp() {
        mqttConfig = new MqttConfig();
        ReflectionTestUtils.setField(mqttConfig, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(mqttConfig, "clientId", "spring-backend-test");
        ReflectionTestUtils.setField(mqttConfig, "safetyEventsTopic", "event");
        ReflectionTestUtils.setField(mqttConfig, "cameraStatusTopic", "safety/cameras/status");
        ReflectionTestUtils.setField(mqttConfig, "overlayTopic", "camera");
        ReflectionTestUtils.setField(mqttConfig, "mqttUsername", "");
        ReflectionTestUtils.setField(mqttConfig, "mqttPassword", "");
    }

    @Test
    void separatesEventAndOverlaySubscriptions() {
        MqttPahoMessageDrivenChannelAdapter eventAdapter =
                (MqttPahoMessageDrivenChannelAdapter) mqttConfig.mqttEventInbound();
        MqttPahoMessageDrivenChannelAdapter overlayAdapter =
                (MqttPahoMessageDrivenChannelAdapter) mqttConfig.mqttOverlayInbound();

        assertThat(eventAdapter.getTopic()).containsExactly("event", "safety/cameras/status");
        assertThat(eventAdapter.getQos()).containsExactly(1, 1);
        assertThat(ReflectionTestUtils.getField(eventAdapter, "clientId"))
                .isEqualTo("spring-backend-test-event");

        assertThat(overlayAdapter.getTopic()).containsExactly("camera");
        assertThat(overlayAdapter.getQos()).containsExactly(0);
        assertThat(ReflectionTestUtils.getField(overlayAdapter, "clientId"))
                .isEqualTo("spring-backend-test-overlay");
    }
}
