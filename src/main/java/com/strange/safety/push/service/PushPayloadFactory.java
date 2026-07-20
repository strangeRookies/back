package com.strange.safety.push.service;

import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.gateway.PushMessagePayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PushPayloadFactory {

    public PushMessagePayload alert(AlertPushRequestedEvent event) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("type", "AI_DANGER_EVENT");
        data.put("eventId", event.alertEventId().toString());
        data.put("cameraId", event.cameraId() == null ? "" : event.cameraId().toString());
        data.put("cameraLoginId", event.cameraLoginId() == null ? "" : event.cameraLoginId());
        data.put("targetType", event.targetType());
        data.put("targetId", event.targetId().toString());
        if ("FACILITY".equals(event.targetType())) {
            data.put("facilityId", event.targetId().toString());
        } else {
            data.put("companyProfileId", event.targetId().toString());
        }
        data.put("scenarioType", event.scenarioType());
        data.put("severity", event.severity());
        data.put("occurredAt", event.occurredAt().toString());

        String cameraLabel = StringUtils.hasText(event.cameraName())
                ? event.cameraName()
                : event.cameraLoginId();
        return new PushMessagePayload(
                koreanScenario(event.scenarioType()) + " 위험 감지",
                cameraLabel + "에서 위험 행동이 감지되었습니다.",
                Map.copyOf(data));
    }

    public PushMessagePayload test() {
        return new PushMessagePayload(
                "Strange Safety 테스트 알림",
                "FCM 알림 연결이 정상입니다.",
                Map.of("type", "FCM_TEST"));
    }

    private String koreanScenario(String scenarioType) {
        return switch (scenarioType) {
            case "FALL_BED" -> "낙상";
            case "SYNCOPE" -> "실신";
            case "COLLAPSE" -> "쓰러짐";
            case "ASSAULT" -> "폭력";
            case "EXIT" -> "이탈";
            case "HAZARD_ZONE" -> "위험구역 침범";
            default -> "AI";
        };
    }
}
