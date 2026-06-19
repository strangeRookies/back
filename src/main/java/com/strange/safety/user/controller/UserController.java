package com.strange.safety.user.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.user.dto.AdminMemberUpdateRequest;
import com.strange.safety.user.dto.AdminUserResponse;
import com.strange.safety.user.dto.MarketingAgreementUpdateRequest;
import com.strange.safety.user.dto.UserAgreementResponse;
import com.strange.safety.user.dto.UserResponse;
import com.strange.safety.user.service.UserAgreementService;
import com.strange.safety.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserAgreementService userAgreementService;

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Page<AdminUserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ApiResponse.success(userService.getAllUsers(pageable));
    }

    @PatchMapping("/admin/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<Void> updateAdminMember(
            @PathVariable Long userId,
            @RequestBody AdminMemberUpdateRequest request) {
        userService.updateAdminMember(userId, request);
        return ApiResponse.success(null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(userService.getMe(user.getUserId()));
    }

    @GetMapping("/me/agreements")
    public ApiResponse<List<UserAgreementResponse>> getMyAgreements(@AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.success(userAgreementService.getMyAgreements(user.getUserId()));
    }

    @PatchMapping("/me/agreements/marketing")
    public ApiResponse<UserAgreementResponse> updateMarketingAgreement(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody MarketingAgreementUpdateRequest request
    ) {
        return ApiResponse.success(userAgreementService.updateMarketingAgreement(user.getUserId(), request));
    }
}
