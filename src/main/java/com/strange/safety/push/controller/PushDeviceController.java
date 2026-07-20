package com.strange.safety.push.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.push.dto.DeletePushDeviceRequest;
import com.strange.safety.push.dto.PushDeviceRegistrationResponse;
import com.strange.safety.push.dto.RegisterPushDeviceRequest;
import com.strange.safety.push.service.PushDeviceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push/devices")
@RequiredArgsConstructor
public class PushDeviceController {

    private final PushDeviceService pushDeviceService;

    @PostMapping
    public ApiResponse<PushDeviceRegistrationResponse> register(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody RegisterPushDeviceRequest request) {
        pushDeviceService.register(userDetails.getUserId(), request);
        return ApiResponse.success(new PushDeviceRegistrationResponse(true));
    }

    @DeleteMapping
    public ApiResponse<Void> deactivate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody DeletePushDeviceRequest request) {
        pushDeviceService.deactivate(userDetails.getUserId(), request.token());
        return ApiResponse.success(null);
    }
}
