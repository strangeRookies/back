package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * AI 서버가 safety/cameras/status 토픽으로 발행하는 카메라 연결 상태 이벤트 DTO.
 * 23.md Section 7 "카메라 상태 이벤트 Payload" 스펙을 준수한다.
 */
public record CameraStatusEventDto(
        @JsonProperty("message_type")
        @JsonAlias({"message_type", "messageType"})
        String messageType,

        @JsonProperty("camera_id")
        @JsonAlias({"camera_id", "cameraId"})
        String cameraId,

        @JsonProperty("camera_login_id")
        @JsonAlias({"camera_login_id", "cameraLoginId"})
        String cameraLoginId,

        @JsonProperty("edge_device_id")
        @JsonAlias({"edge_device_id", "edgeDeviceId"})
        String edgeDeviceId,

        /**
         * AI 서버가 보고하는 실시간 연결 상태.
         * CONNECTED | DISCONNECTED | RECONNECTING | ERROR | DISABLED
         */
        String status,

        @JsonProperty("previous_status")
        @JsonAlias({"previous_status", "previousStatus"})
        String previousStatus,

        /**
         * 상태 변경 사유.
         * e.g. RTSP_TIMEOUT, RECONNECT_ATTEMPT, AUTH_FAILED
         */
        String reason,

        @JsonProperty("rtsp_url_masked")
        @JsonAlias({"rtsp_url_masked", "rtspUrlMasked"})
        String rtspUrlMasked,

        @JsonProperty("detected_at")
        @JsonAlias({"detected_at", "detectedAt", "timestamp"})
        Object rawDetectedAt
) {
    public Instant resolvedDetectedAt() {
        if (rawDetectedAt == null) return Instant.now();

        if (rawDetectedAt instanceof Number num) {
            double epochSeconds = num.doubleValue();
            long seconds = (long) epochSeconds;
            long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, nanos);
        }

        if (rawDetectedAt instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (Exception e) {
                return Instant.now();
            }
        }

        return Instant.now();
    }
}
