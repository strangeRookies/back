package com.strange.safety.camera.entity;

/**
 * AI 서버가 MQTT safety/cameras/status 토픽으로 보고하는 실시간 RTSP 연결 상태.
 * 23.md Section 6 "카메라 상태 값 정의" 에 대응한다.
 *
 * {@link CameraStatus} (ACTIVE/INACTIVE) 는 관리자가 카메라를 등록/비활성화하는 운영 상태이며,
 * 이 enum은 AI Edge 서버의 실시간 연결 상태를 나타낸다.
 */
public enum CameraConnectionStatus {
    /** RTSP 연결 및 프레임 수신 정상 */
    CONNECTED,

    /** RTSP 연결 실패 또는 프레임 수신 중단 */
    DISCONNECTED,

    /** 재연결 시도 중 */
    RECONNECTING,

    /** 인증 실패, 주소 오류, 코덱 오류 등 명확한 장애 */
    ERROR,

    /** 관리자가 비활성화한 카메라 (AI 서버에서 감지) */
    DISABLED,

    /** 아직 상태 보고가 오지 않은 초기 상태 */
    UNKNOWN
}
