package com.strange.safety.event;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/**
 * AI Edge 서버가 MQTT safety/events 토픽으로 발행하는 이상행동 이벤트 DTO.
 *
 * <p>AI 서버 페이로드 예시 (serve_ai_overlay.py / build_inference_event_payload):
 * <pre>
 * {
 *   "camera_id":  "cam_01",
 *   "timestamp":  1749550205.3,   ← Unix epoch 초 (float)
 *   "event_type": "Faint",        ← JsonAlias("event_type") 로 매핑
 *   "severity":   "HIGH",
 *   "confidence": 0.87,
 *   "bbox":       [x1, y1, x2, y2],
 *   "track_id":   2
 * }
 * </pre>
 *
 * <p>timestamp 처리 전략:
 * <ul>
 *   <li>float (Unix epoch 초) → {@link #resolvedTimestamp()} 에서 {@link Instant}로 변환</li>
 *   <li>ISO-8601 문자열 → Jackson이 직접 {@link Instant}로 역직렬화</li>
 *   <li>null → {@link AlertEventService}에서 {@code Instant.now()} 로 대체</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SafetyEventDto(
        @JsonProperty("message_type")
        @JsonAlias({"message_type", "messageType"})
        String messageType,

        @JsonProperty("eventPhase")
        @JsonAlias({"eventPhase", "event_phase"})
        String eventPhase,

        @JsonProperty("frameId")
        @JsonAlias({"frameId", "frame_id"})
        String frameId,

        /**
         * 이상행동 유형. AI 서버는 "event_type" 키로 전송한다.
         * e.g. "Faint", "Fall", "Fight"
         */
        @JsonAlias({"type", "event_type", "eventType"})
        String type,

        @JsonProperty("camera_id")
        @JsonAlias({"camera_id", "cameraId"})
        String cameraId,

        @JsonProperty("camera_login_id")
        @JsonAlias({"camera_login_id", "cameraLoginId"})
        String cameraLoginId,

        /**
         * AI 서버가 보내는 고유 이벤트 ID.
         */
        @JsonProperty("eventId")
        @JsonAlias({"eventId", "event_id"})
        String eventId,

        /**
         * AI 서버가 보내는 timestamp.
         * float (Unix epoch 초)이면 Jackson이 직렬화 실패하므로
         * rawTimestamp(Number)로 받아서 resolvedTimestamp()에서 Instant로 변환한다.
         *
         * ISO-8601 문자열이면 Jackson이 직접 파싱한다.
         */
        @JsonProperty("timestamp")
        @JsonAlias({"timestamp", "detected_at", "detectedAt"})
        Object rawTimestamp,

        String severity,

        String message,

        String source,

        /**
         * 신뢰도. AI 서버는 "confidence" 키로 float를 전송한다.
         * "score"도 별칭으로 허용한다.
         */
        @JsonAlias({"confidence", "score"})
        Float confidence,

        @JsonProperty("faint_prob")
        @JsonAlias({"faint_prob", "faintProb"})
        Double faintProb,

        List<Number> bbox,

        @JsonProperty("track_id")
        @JsonAlias({"track_id", "trackId"})
        String trackId,

        @JsonProperty("clip_path")
        @JsonAlias({"clip_path", "clipPath"})
        String clipPath,

        @JsonProperty("clip_object_key")
        @JsonAlias({"clip_object_key", "clipObjectKey"})
        String clipObjectKey,

        @JsonProperty("clip_url")
        @JsonAlias({"clip_url", "clipUrl"})
        String clipUrl,

        @JsonProperty("capturedAtMs")
        @JsonAlias({"capturedAtMs", "captured_at_ms"})
        Long capturedAtMs,

        @JsonProperty("processedAtMs")
        @JsonAlias({"processedAtMs", "processed_at_ms"})
        Long processedAtMs,

        @JsonProperty("mqttPublishStartedAtMs")
        @JsonAlias({"mqttPublishStartedAtMs", "mqtt_publish_started_at_ms"})
        Long mqttPublishStartedAtMs,

        @JsonProperty("mqttPublishedAtMs")
        @JsonAlias({"mqttPublishedAtMs", "mqtt_published_at_ms"})
        Long mqttPublishedAtMs,

        @JsonProperty("publishedAtMs")
        @JsonAlias({"publishedAtMs", "published_at_ms"})
        Long publishedAtMs,

        @JsonProperty("mqttReceivedAtMs")
        @JsonAlias({"mqttReceivedAtMs", "mqtt_received_at_ms"})
        Long mqttReceivedAtMs
) {
    /**
     * rawTimestamp를 {@link Instant}로 변환한다.
     *
     * <ul>
     *   <li>Number (float/int → Unix epoch 초): 정수부를 초로, 소수부를 나노초로 변환</li>
     *   <li>null: null 반환 (서비스 레이어에서 Instant.now() 대체)</li>
     * </ul>
     */
    public Instant resolvedTimestamp() {
        if (rawTimestamp == null) return null;
        
        if (rawTimestamp instanceof Number num) {
            double epochSeconds = num.doubleValue();
            long seconds = (long) epochSeconds;
            long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, nanos);
        }
        
        if (rawTimestamp instanceof String str) {
            try {
                return Instant.parse(str);
            } catch (Exception e) {
                return null;
            }
        }
        
        return null;
    }

    public SafetyEventDto withMqttReceivedAtMs(long mqttReceivedAtMs) {
        return copy(capturedAtMs, processedAtMs, mqttPublishStartedAtMs, mqttPublishedAtMs, publishedAtMs, mqttReceivedAtMs);
    }

    public SafetyEventDto withPublishedAtMs(long publishedAtMs) {
        return copy(capturedAtMs, processedAtMs, mqttPublishStartedAtMs, mqttPublishedAtMs, publishedAtMs, mqttReceivedAtMs);
    }

    @JsonProperty("eventPhase")
    public String eventPhase() {
        if (eventPhase != null && !eventPhase.isBlank()) {
            return eventPhase;
        }
        return isEvidenceEvent() ? "evidence" : "realtime";
    }

    @JsonProperty("evidenceEvent")
    public boolean isEvidenceEvent() {
        if (eventPhase != null && "evidence".equalsIgnoreCase(eventPhase.trim())) {
            return true;
        }
        if (eventPhase != null && "realtime".equalsIgnoreCase(eventPhase.trim())) {
            return false;
        }
        return hasText(clipObjectKey) || hasText(clipUrl) || hasText(clipPath);
    }

    @JsonProperty("realtimeEvent")
    public boolean isRealtimeEvent() {
        if (eventPhase != null && "realtime".equalsIgnoreCase(eventPhase.trim())) {
            return true;
        }
        return !isEvidenceEvent();
    }

    private SafetyEventDto copy(
            Long capturedAtMs,
            Long processedAtMs,
            Long mqttPublishStartedAtMs,
            Long mqttPublishedAtMs,
            Long publishedAtMs,
            Long mqttReceivedAtMs) {
        return new SafetyEventDto(
                messageType,
                eventPhase,
                frameId,
                type,
                cameraId,
                cameraLoginId,
                eventId,
                rawTimestamp,
                severity,
                message,
                source,
                confidence,
                faintProb,
                bbox,
                trackId,
                clipPath,
                clipObjectKey,
                clipUrl,
                capturedAtMs,
                processedAtMs,
                mqttPublishStartedAtMs,
                mqttPublishedAtMs,
                publishedAtMs,
                mqttReceivedAtMs);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
