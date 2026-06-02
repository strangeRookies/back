package com.strange.safety.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "만료된 토큰입니다."),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "접근 권한이 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),

    FACILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "FACILITY_NOT_FOUND", "시설을 찾을 수 없습니다."),
    FACILITY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FACILITY_ACCESS_DENIED", "해당 시설에 접근 권한이 없습니다."),

    CAMERA_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMERA_NOT_FOUND", "카메라를 찾을 수 없습니다."),
    CAMERA_CONNECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CAMERA_CONNECTION_FAILED", "카메라 연결에 실패했습니다."),

    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "알림을 찾을 수 없습니다."),

    PROTECTED_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "PROTECTED_TARGET_NOT_FOUND", "보호 대상자를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
