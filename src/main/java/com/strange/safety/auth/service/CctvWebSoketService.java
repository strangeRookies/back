package com.strange.safety.auth.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.strange.safety.auth.dto.Cctv;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CctvWebSoketService {

    // STOMP 메시지 전송을 위한 템플릿
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * AI 좌표 데이터를 프론트엔드로 실시간 전송합니다.
     */
    public void sendTelemetryData(Cctv.TelemetryData data) {
        // 프론트엔드의 stompClient.subscribe('/topic/cctv/telemetry', ...) 로 데이터가 날아갑니다.
        messagingTemplate.convertAndSend("/topic/cctv/telemetry", data);
    }

    /**
     * 위험 이벤트(DB 저장 등)를 프론트엔드로 알림 전송합니다.
     */
    public void sendAlertEvent(Object alertData) {
        messagingTemplate.convertAndSend("/topic/cctv/event", alertData);
    }
}
