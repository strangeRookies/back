package com.strange.safety.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "만료된 토큰입니다."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "접근 권한이 없습니다."),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "AUTH_FORBIDDEN", "접근 권한이 없습니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "로그인 정보가 올바르지 않습니다."),
    AUTH_INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_PASSWORD", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INVALID_VERIFICATION(HttpStatus.BAD_REQUEST, "AUTH_INVALID_VERIFICATION", "유효하지 않은 휴대폰 인증입니다."),

    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER_INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),

    COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS", "이미 등록된 사업자등록번호입니다."),

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
