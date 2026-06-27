package com.strange.safety.camera.overlay;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OverlayMqttConnectionListener {

    private final OverlayRelayService overlayRelayService;

    @EventListener
    public void onConnectionFailed(MqttConnectionFailedEvent event) {
        overlayRelayService.clearAll();
    }
}
