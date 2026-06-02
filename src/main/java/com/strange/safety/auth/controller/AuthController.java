package com.strange.safety.auth.controller;

import com.strange.safety.auth.dto.LoginRequest;
import com.strange.safety.auth.dto.LogoutRequest;
import com.strange.safety.auth.dto.SignupRequest;
import com.strange.safety.auth.dto.TokenReissueRequest;
import com.strange.safety.auth.dto.TokenResponse;
import com.strange.safety.auth.service.AuthService;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success("회원가입이 완료되었습니다.", authService.signup(request));
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
