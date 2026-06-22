package com.strange.safety.corporatecamera.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.corporatecamera.dto.CorporateCameraResponse;
import com.strange.safety.corporatecamera.service.CorporateCameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/corporate-cameras")
public class CorporateCameraUserController {

    private final CorporateCameraService corporateCameraService;

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<CorporateCameraResponse>>> getMyCameras(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(corporateCameraService.getMyCameras(userDetails.getUserId())));
    }
}
