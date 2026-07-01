package com.strange.safety.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    COMMON_INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_INVALID_INPUT", "요청 값이 올바르지 않습니다."),
    COMMON_METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_METHOD_NOT_ALLOWED", "지원하지 않는 HTTP 메서드입니다."),
    COMMON_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_ERROR", "서버 내부 오류가 발생했습니다."),

    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_TOKEN", "유효하지 않은 토큰입니다."),
    AUTH_EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_EXPIRED_TOKEN", "만료된 토큰입니다."),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "AUTH_UNAUTHORIZED", "인증이 필요합니다."),
    AUTH_ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_ACCESS_DENIED", "접근 권한이 없습니다."),
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS", "로그인 정보가 올바르지 않습니다."),
    AUTH_ACCOUNT_SUSPENDED(HttpStatus.UNAUTHORIZED, "AUTH_ACCOUNT_SUSPENDED", "비활성화된 계정입니다. 계정 정보를 확인하세요."),
    AUTH_LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_LOGIN_LOCKED", "로그인 실패가 반복되어 일시적으로 로그인이 제한됩니다."),
    AUTH_INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_PASSWORD", "이메일 또는 비밀번호가 올바르지 않습니다."),
    AUTH_INVALID_VERIFICATION(HttpStatus.BAD_REQUEST, "AUTH_INVALID_VERIFICATION", "유효하지 않은 휴대폰 인증입니다."),
    SMS_SEND_FAILED(HttpStatus.BAD_GATEWAY, "SMS_SEND_FAILED", "인증번호 발송에 실패했습니다."),
    SMS_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "SMS_RATE_LIMITED", "인증번호 발송 요청이 너무 많습니다."),
    SMS_PROVIDER_CONFIG_INVALID(HttpStatus.INTERNAL_SERVER_ERROR, "SMS_PROVIDER_CONFIG_INVALID", "SMS 발송 설정이 올바르지 않습니다."),

    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_ALREADY_EXISTS", "이미 사용 중인 이메일입니다."),
    USER_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "USER_EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    USER_INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "USER_INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),

    COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "COMPANY_BUSINESS_NUMBER_ALREADY_EXISTS", "이미 등록된 사업자등록번호입니다."),
    COMPANY_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "COMPANY_PROFILE_NOT_FOUND", "기업 프로필을 찾을 수 없습니다."),

    AGREEMENT_REQUIRED(HttpStatus.BAD_REQUEST, "AGREEMENT_REQUIRED", "필수 약관에 동의해야 합니다."),
    AGREEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "약관 동의 이력을 찾을 수 없습니다."),

    FACILITY_NOT_FOUND(HttpStatus.NOT_FOUND, "FACILITY_NOT_FOUND", "시설을 찾을 수 없습니다."),
    FACILITY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "FACILITY_ACCESS_DENIED", "해당 시설에 접근 권한이 없습니다."),
    CAMERA_NOT_FOUND(HttpStatus.NOT_FOUND, "CAMERA_NOT_FOUND", "카메라를 찾을 수 없습니다."),
    DUPLICATE_CAMERA_LOGIN_ID(HttpStatus.CONFLICT, "DUPLICATE_CAMERA_LOGIN_ID", "이미 등록된 카메라 로그인 ID입니다."),
    CAMERA_CONNECTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CAMERA_CONNECTION_FAILED", "카메라 연결에 실패했습니다."),
    ALERT_NOT_FOUND(HttpStatus.NOT_FOUND, "ALERT_NOT_FOUND", "알림을 찾을 수 없습니다."),
    PROTECTED_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "PROTECTED_TARGET_NOT_FOUND", "보호 대상자를 찾을 수 없습니다."),

    EMERGENCY_CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND, "EMERGENCY_CONTACT_NOT_FOUND", "비상 연락처를 찾을 수 없습니다."),
    EMERGENCY_JURISDICTION_NOT_FOUND(HttpStatus.NOT_FOUND, "EMERGENCY_JURISDICTION_NOT_FOUND", "입력한 주소의 관할 응급기관을 찾을 수 없습니다."),

    ROI_CONFIG_NOT_FOUND(HttpStatus.NOT_FOUND, "ROI_CONFIG_NOT_FOUND", "ROI 설정을 찾을 수 없습니다."),
    ROI_INVALID_COORDINATES(HttpStatus.BAD_REQUEST, "ROI_INVALID_COORDINATES", "polygon_points의 좌표 값은 0.0~1.0 사이여야 합니다."),

    SCENARIO_NOT_FOUND(HttpStatus.NOT_FOUND, "SCENARIO_NOT_FOUND", "시나리오를 찾을 수 없습니다."),
    SCENARIO_PARAMS_NOT_FOUND(HttpStatus.NOT_FOUND, "SCENARIO_PARAMS_NOT_FOUND", "시나리오 파라미터를 찾을 수 없습니다."),

    INQUIRY_NOT_FOUND(HttpStatus.NOT_FOUND, "INQUIRY_NOT_FOUND", "문의 내역을 찾을 수 없습니다."),

    EXCEL_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "EXCEL_INVALID_FORMAT", "엑셀 파일 형식이 올바르지 않습니다. (.xls, .xlsx만 허용)"),
    EXCEL_PARSE_ERROR(HttpStatus.BAD_REQUEST, "EXCEL_PARSE_ERROR", "엑셀 파일 파싱 중 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
