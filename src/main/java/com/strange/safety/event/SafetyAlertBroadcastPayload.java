package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.strange.safety.scenario.entity.ScenarioType;

/** Frontend-facing STOMP payload with backend-resolved alert presentation data. */
public record SafetyAlertBroadcastPayload(
        @JsonIgnoreProperties("message") @JsonUnwrapped SafetyEventDto event,
        String scenarioType,
        String message
) {
    public static SafetyAlertBroadcastPayload of(SafetyEventDto event, ScenarioType scenarioType, String message) {
        return new SafetyAlertBroadcastPayload(event, scenarioType.name(), message);
    }
}
