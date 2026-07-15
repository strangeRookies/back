package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.strange.safety.scenario.entity.ScenarioType;

@Service
public class AlertBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(AlertBroadcastService.class);

    private final SimpMessagingTemplate messagingTemplate;

    public AlertBroadcastService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void broadcast(Long targetId, boolean isCorporate, SafetyEventDto event, ScenarioType scenarioType, String displayMessage) {
        SafetyEventDto publishedEvent = event.withPublishedAtMs(System.currentTimeMillis());
        SafetyAlertBroadcastPayload payload = SafetyAlertBroadcastPayload.of(publishedEvent, scenarioType, displayMessage);

        // 1. 프론트엔드 공통/전역 구독 토픽 동시 발송 (/topic/alerts, /topic/cctv/event)
        messagingTemplate.convertAndSend("/topic/alerts", payload);
        messagingTemplate.convertAndSend("/topic/cctv/event", payload);

        // 2. 시설/기업 ID가 확인된 경우 개별 구독 토픽 발송
        if (targetId != null) {
            String prefix = isCorporate ? "/topic/company/" : "/topic/facility/";
            String topic = prefix + targetId + "/alerts";
            log.info("Broadcasting safety event to {}: type={}, cameraId={}, severity={}",
                    topic, publishedEvent.type(), publishedEvent.cameraId(), publishedEvent.severity());
            log.info("[ai-alert-latency] STOMP safety event publishing destination={} cameraId={} cameraLoginId={} eventId={} frameId={} eventPhase={} realtimeEvent={} evidenceEvent={} type={} severity={} trackId={} capturedAtMs={} processedAtMs={} mqttPublishStartedAtMs={} mqttPublishedAtMs={} mqttReceivedAtMs={} stompPublishedAtMs={} processedToMqttMs={} mqttToBackendMs={} backendToStompMs={} processedToBackendMs={} clipPath={} clipUrl={}",
                    topic,
                    publishedEvent.cameraId(),
                    publishedEvent.cameraLoginId(),
                    publishedEvent.eventId(),
                    publishedEvent.frameId(),
                    publishedEvent.eventPhase(),
                    publishedEvent.isRealtimeEvent(),
                    publishedEvent.isEvidenceEvent(),
                    publishedEvent.type(),
                    publishedEvent.severity(),
                    publishedEvent.trackId(),
                    publishedEvent.capturedAtMs(),
                    publishedEvent.processedAtMs(),
                    publishedEvent.mqttPublishStartedAtMs(),
                    publishedEvent.mqttPublishedAtMs(),
                    publishedEvent.mqttReceivedAtMs(),
                    publishedEvent.publishedAtMs(),
                    elapsed(publishedEvent.processedAtMs(), publishedEvent.mqttPublishedAtMs()),
                    elapsed(publishedEvent.mqttPublishedAtMs(), publishedEvent.mqttReceivedAtMs()),
                    elapsed(publishedEvent.mqttReceivedAtMs(), publishedEvent.publishedAtMs()),
                    elapsed(publishedEvent.processedAtMs(), publishedEvent.mqttReceivedAtMs()),
                    publishedEvent.clipPath(),
                    publishedEvent.clipUrl());
            messagingTemplate.convertAndSend(topic, payload);
        } else {
            log.warn("TargetId is null for camera: {}. Broadcasted to /topic/alerts and /topic/cctv/event only.", event.cameraId());
        }
    }

    private Long elapsed(Long startAtMs, Long endAtMs) {
        if (startAtMs == null || endAtMs == null) {
            return null;
        }
        return endAtMs - startAtMs;
    }
}
