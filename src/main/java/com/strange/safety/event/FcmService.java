package com.strange.safety.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * FCM(Firebase Cloud Messaging) 기반 모바일 푸시 알림 서비스.
 *
 * - {@code app.fcm.enabled=true}로 설정 시 실제 FCM API를 호출한다.
 * - 기본값(false)일 때는 로그만 남기는 Mock 동작을 한다.
 *
 * <p>실제 운영에서 FCM을 활성화하려면:
 * <ol>
 *   <li>build.gradle에 Firebase Admin SDK 의존성 추가:
 *       {@code implementation 'com.google.firebase:firebase-admin:9.2.0'}</li>
 *   <li>serviceAccountKey.json 파일을 classpath 또는 외부 경로에 배치</li>
 *   <li>application.yml에서 {@code app.fcm.enabled=true} 및
 *       {@code app.fcm.service-account-key} 설정</li>
 * </ol>
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private final boolean enabled;

    public FcmService(
            @Value("${app.fcm.enabled:false}") boolean enabled
    ) {
        this.enabled = enabled;
        if (enabled) {
            log.info("[FCM] FCM push notification service is ENABLED.");
        } else {
            log.info("[FCM] FCM push notification service is DISABLED (mock mode). Set app.fcm.enabled=true to enable.");
        }
    }

    /**
     * AI 이상행동 감지 이벤트에 대한 FCM 푸시 알림을 발송한다.
     *
     * @param event 이상행동 이벤트 DTO
     */
    public void sendAlertNotification(SafetyEventDto event) {
        String title = buildAlertTitle(event);
        String body = buildAlertBody(event);
        sendToTopic("safety_alerts", title, body);
    }

    /**
     * 지정된 FCM 토픽으로 알림을 발송한다.
     *
     * @param topic FCM 토픽명 (예: "safety_alerts", "camera_errors")
     * @param title 알림 제목
     * @param body  알림 본문
     */
    public void sendToTopic(String topic, String title, String body) {
        if (!enabled) {
            log.info("[FCM][Mock] 푸시 알림 (topic={}, title={}, body={})", topic, title, body);
            return;
        }

        // TODO: 실제 FCM 전송 로직 (firebase-admin SDK 활성화 후 아래 주석 해제)
        // try {
        //     Message message = Message.builder()
        //             .setNotification(Notification.builder()
        //                     .setTitle(title)
        //                     .setBody(body)
        //                     .build())
        //             .setTopic(topic)
        //             .build();
        //     String response = FirebaseMessaging.getInstance().send(message);
        //     log.info("[FCM] 푸시 알림 발송 성공: messageId={}, topic={}", response, topic);
        // } catch (FirebaseMessagingException ex) {
        //     log.error("[FCM] 푸시 알림 발송 실패: topic={}, error={}", topic, ex.getMessage(), ex);
        // }

        log.warn("[FCM] app.fcm.enabled=true이지만 Firebase Admin SDK가 초기화되지 않았습니다. build.gradle에 의존성을 추가하세요.");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private String buildAlertTitle(SafetyEventDto event) {
        String type = event.type() != null ? event.type().toUpperCase() : "이상행동";
        String severity = event.severity() != null ? event.severity() : "CRITICAL";
        String severityLabel = severity.equalsIgnoreCase("CRITICAL") ? "🔴 위험" : "🟡 경고";
        return String.format("[%s] %s 감지", severityLabel, koreanEventType(type));
    }

    private String buildAlertBody(SafetyEventDto event) {
        String camera = event.cameraId() != null ? event.cameraId() : "알 수 없음";
        float confidence = event.confidence() != null ? event.confidence() * 100 : 0f;
        return String.format("카메라: %s | 신뢰도: %.0f%%", camera, confidence);
    }

    private String koreanEventType(String type) {
        if (type.contains("FALL")) return "낙상";
        if (type.contains("FAINT") || type.contains("SYNCOPE")) return "실신";
        if (type.contains("COLLAPSE")) return "쓰러짐";
        if (type.contains("FIGHT") || type.contains("VIOLENCE") || type.contains("ASSAULT")) return "폭력";
        if (type.contains("FIRE")) return "화재";
        return type;
    }
}
