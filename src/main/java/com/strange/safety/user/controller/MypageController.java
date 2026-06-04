package com.strange.safety.user.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.user.dto.*;
import com.strange.safety.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mypage")
@RequiredArgsConstructor
public class MypageController {

    private final UserService userService;

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> getProfile(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(userService.getProfile(userDetails.getUserId()));
    }

    @PutMapping("/profile")
    public ApiResponse<Void> updateProfile(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(userDetails.getUserId(), request);
        return ApiResponse.success(null);
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody UpdatePasswordRequest request) {
        userService.changePassword(userDetails.getUserId(), request);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/account")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteAccount(userDetails.getUserId());
        return ApiResponse.success(null);
    }
}
