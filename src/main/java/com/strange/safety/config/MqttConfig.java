package com.strange.safety.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;

@Configuration
@IntegrationComponentScan
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    @Value("${mqtt.topic:safety/events}")
    private String safetyEventsTopic;

    @Value("${mqtt.camera-status-topic:safety/cameras/status}")
    private String cameraStatusTopic;

    @Value("${mqtt.overlay-topic:camera}")
    private String overlayTopic;

    @Value("${MQTT_USERNAME:}")
    private String mqttUsername;

    @Value("${MQTT_PASSWORD:}")
    private String mqttPassword;

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[] { brokerUrl });
        if (!mqttUsername.isBlank()) {
            options.setUserName(mqttUsername);
        }
        if (!mqttPassword.isBlank()) {
            options.setPassword(mqttPassword.toCharArray());
        }
        options.setAutomaticReconnect(true);
        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(mqttConnectOptions());
        return factory;
    }

    @Bean
    public MessageProducer mqttEventInbound() {
        log.info("Configuring MQTT event inbound adapter: brokerUrl={}, clientId={}, topics=[{}, {}], qos=[1, 1], automaticReconnect=true",
                brokerUrl,
                clientId + "-event",
                safetyEventsTopic,
                cameraStatusTopic);
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId + "-event",
                mqttClientFactory(),
                safetyEventsTopic,
                cameraStatusTopic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1, 1);
        adapter.setOutputChannel(mqttEventInputChannel());
        return adapter;
    }

    @Bean
    public MessageProducer mqttOverlayInbound() {
        log.info("Configuring MQTT overlay inbound adapter: brokerUrl={}, clientId={}, topic={}, qos=0, automaticReconnect=true",
                brokerUrl,
                clientId + "-overlay",
                overlayTopic);
        MqttPahoMessageDrivenChannelAdapter adapter = new MqttPahoMessageDrivenChannelAdapter(
                clientId + "-overlay",
                mqttClientFactory(),
                overlayTopic);

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(0);
        adapter.setOutputChannel(mqttOverlayInputChannel());
        return adapter;
    }

    @Bean
    public MessageChannel mqttEventInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOverlayInputChannel() {
        return new DirectChannel();
    }
}
