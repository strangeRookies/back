package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.strange.safety.scenario.entity.ScenarioType;

/** Frontend-facing STOMP payload with backend-resolved alert presentation data. */
public record SafetyAlertBroadcastPayload(
        @JsonIgnoreProperties("message") @JsonUnwrapped SafetyEventDto event,
        String scenarioType,
        String message,
        String vlmDescription,
        String summaryKo,
        String reason
) {
    private static final String DEFAULT_VLM_REASON = "1명이 바닥 가까이에 누운 자세로 감지되었고, 쓰러짐 신호가 연속 확인되어 쓰러짐 의심 이벤트가 발생했습니다.";

    public static SafetyAlertBroadcastPayload of(SafetyEventDto event, ScenarioType scenarioType, String message) {
        return new SafetyAlertBroadcastPayload(
                event,
                scenarioType.name(),
                message,
                DEFAULT_VLM_REASON,
                DEFAULT_VLM_REASON,
                DEFAULT_VLM_REASON
        );
    }
}
