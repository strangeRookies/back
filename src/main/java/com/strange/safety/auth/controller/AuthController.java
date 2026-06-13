package com.strange.safety.auth.controller;

import com.strange.safety.auth.dto.*;
import com.strange.safety.auth.service.AuthService;
import com.strange.safety.auth.service.PasswordResetService;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.auth.service.SmsVerificationService;
import com.strange.safety.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SignupService signupService;
    private final SmsVerificationService smsVerificationService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/signup/individual")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signupIndividual(@Valid @RequestBody IndividualSignupRequest request) {
        return ApiResponse.success("개인 회원가입이 완료되었습니다.", signupService.signupIndividual(request));
    }

    @PostMapping("/signup/corporate")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signupCorporate(@Valid @RequestBody CorporateSignupRequest request) {
        return ApiResponse.success("기업 회원가입이 완료되었습니다.", signupService.signupCorporate(request));
    }

    @PostMapping("/verifications/sms")
    public ApiResponse<SmsVerificationResponse> sendSms(@Valid @RequestBody SmsVerificationRequest request) {
        return ApiResponse.success("인증번호가 발급되었습니다.", smsVerificationService.send(request));
    }

    @PostMapping("/verifications/sms/confirm")
    public ApiResponse<SmsVerificationConfirmResponse> confirmSms(
            @Valid @RequestBody SmsVerificationConfirmRequest request
    ) {
        return ApiResponse.success("휴대폰 인증이 완료되었습니다.", smsVerificationService.confirm(request));
    }

    @PostMapping("/password-reset/verifications/sms")
    public ApiResponse<PasswordResetSmsResponse> sendPasswordResetSms(
            @Valid @RequestBody PasswordResetSmsRequest request
    ) {
        return ApiResponse.success("비밀번호 재설정 인증번호가 발급되었습니다.", passwordResetService.sendSms(request));
    }

    @PostMapping("/password-reset/verifications/sms/confirm")
    public ApiResponse<SmsVerificationConfirmResponse> confirmPasswordResetSms(
            @Valid @RequestBody PasswordResetSmsConfirmRequest request
    ) {
        return ApiResponse.success("휴대폰 인증이 완료되었습니다.", passwordResetService.confirmSms(request));
    }

    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.success("비밀번호가 재설정되었습니다.", null);
    }

    @PostMapping("/email-availability")
    public ApiResponse<AvailabilityResponse> emailAvailability(@Valid @RequestBody EmailAvailabilityRequest request) {
        return ApiResponse.success(signupService.emailAvailability(request.email()));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success("로그인에 성공했습니다.", authService.login(request));
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody TokenReissueRequest request) {
        return ApiResponse.success("토큰이 재발급되었습니다.", authService.reissue(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success("로그아웃되었습니다.", null);
    }
}
