package com.strange.safety.camera.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.dto.CreateCameraRequest;
import com.strange.safety.camera.dto.UpdateCameraRequest;
import com.strange.safety.camera.service.CameraService;
import com.strange.safety.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @PostMapping({"/api/facilities/{facilityId}/cameras", "/api/cameras"})
    public ResponseEntity<ApiResponse<CameraResponse>> createCamera(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(required = false) Long facilityId,
            @Valid @RequestBody CreateCameraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        cameraService.createCamera(userDetails.getUserId(), facilityId, request)));
    }

    @GetMapping({"/api/facilities/{facilityId}/cameras", "/api/cameras"})
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getCameras(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable(required = false) Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success(
                cameraService.getCameras(userDetails.getUserId(), facilityId)));
    }

    @GetMapping("/api/cameras/active")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getActiveAiCameras() {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getActiveAiCameras()));
    }

    @PutMapping("/api/cameras/{cameraId}")
    public ResponseEntity<ApiResponse<CameraResponse>> updateCamera(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId,
            @Valid @RequestBody UpdateCameraRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                cameraService.updateCamera(userDetails.getUserId(), cameraId, request)));
    }

    @DeleteMapping("/api/cameras/{cameraId}")
    public ResponseEntity<ApiResponse<Void>> deleteCamera(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId) {
        cameraService.deleteCamera(userDetails.getUserId(), cameraId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
