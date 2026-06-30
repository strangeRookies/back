package com.strange.safety.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

public class Cctv {
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelemetryData {
        private long timestampMs;
        private String streamId; // 예: "cam-01" (프론트엔드 카메라 ID와 일치해야 함)
        private int frameWidth; // 예: 1280 (AI 모델이 분석한 원본 가로)
        private int frameHeight; // 예: 720 (AI 모델이 분석한 원본 세로)
        private List<TelemetryEvent> events;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TelemetryEvent {
        private String type; // 예: "faint"
        private String memoText; // 예: "쓰러짐 의심!"
        private double confidence; // 예: 0.95 (확률)
        private Long trackingId; // 사람 추적 ID
        private BoundingBox boundingBox;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoundingBox {
        private double x;
        private double y;
        private double width;
        private double height;
    }
}