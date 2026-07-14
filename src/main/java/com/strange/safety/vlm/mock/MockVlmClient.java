package com.strange.safety.vlm.mock;

import java.util.List;

public class MockVlmClient {
    public VlmAnalysisResult analyze(Incident incident) {
        boolean recovered = hasEvent(incident, IncidentEventType.RECOVERED);
        boolean faintSuspected = hasEvent(incident, IncidentEventType.FAINT_SUSPECTED);
        boolean unrecovered = hasEvent(incident, IncidentEventType.FALL_UNRECOVERED);
        String movement = faintSuspected || unrecovered ? "low" : "medium";
        double lyingDuration = faintSuspected || unrecovered ? 18.0d : 4.0d;
        return new VlmAnalysisResult(
                "incident-v1",
                incident.incidentId(),
                summary(recovered, unrecovered),
                unrecovered ? "fall_and_remain_lying" : "fall_then_recover",
                "walking",
                unrecovered ? "lying" : "standing_after_recovery",
                movement,
                recovered,
                lyingDuration,
                unrecovered ? "CRITICAL" : "HIGH",
                new PersonDescription("black", "dark"),
                List.of(),
                List.of(2, 3, 4, 6, 7)
        );
    }

    private boolean hasEvent(Incident incident, IncidentEventType type) {
        return incident.events().stream().anyMatch(event -> event.eventType() == type);
    }

    private String summary(boolean recovered, boolean unrecovered) {
        if (unrecovered) {
            return "보행 중 쓰러진 뒤 일정 시간 움직임이 낮고 회복이 관찰되지 않았습니다.";
        }
        return recovered
                ? "보행 중 쓰러진 뒤 일정 시간 움직임이 낮았으나 이후 회복했습니다."
                : "보행 중 쓰러진 뒤 짧은 시간 안에 움직임이 다시 관찰되었습니다.";
    }
}
