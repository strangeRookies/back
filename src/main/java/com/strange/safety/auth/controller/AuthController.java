package com.strange.safety.auth.controller;

import com.strange.safety.auth.dto.*;
import com.strange.safety.auth.service.AuthService;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.auth.service.SmsVerificationService;
import com.strange.safety.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    public ApiResponse<SmsVerificationResponse> sendSms(
            @Valid @RequestBody SmsVerificationRequest request,
            HttpServletRequest servletRequest
    ) {
        return ApiResponse.success("인증번호가 발급되었습니다.",
                smsVerificationService.send(request, clientIp(servletRequest)));
    }

    @PostMapping("/verifications/sms/confirm")
    public ApiResponse<SmsVerificationConfirmResponse> confirmSms(
            @Valid @RequestBody SmsVerificationConfirmRequest request
    ) {
        return ApiResponse.success("휴대폰 인증이 완료되었습니다.", smsVerificationService.confirm(request));
    }

    @GetMapping("/email-availability")
    public ApiResponse<AvailabilityResponse> emailAvailability(@RequestParam String email) {
        return ApiResponse.success(signupService.emailAvailability(email));
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

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
