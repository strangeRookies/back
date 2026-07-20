package com.strange.safety.camera.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.camera.dto.CreateRoiConfigRequest;
import com.strange.safety.camera.dto.RoiConfigResponse;
import com.strange.safety.camera.dto.UpdateRoiConfigRequest;
import com.strange.safety.camera.service.RoiConfigService;
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
public class RoiConfigController {

    private final RoiConfigService roiConfigService;

    @PostMapping("/api/cameras/{cameraId}/roi-configs")
    public ResponseEntity<ApiResponse<RoiConfigResponse>> create(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId,
            @Valid @RequestBody CreateRoiConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        roiConfigService.create(userDetails.getUserId(), cameraId, request)));
    }

    @GetMapping("/api/cameras/{cameraId}/roi-configs")
    public ResponseEntity<ApiResponse<List<RoiConfigResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(
                roiConfigService.getList(userDetails.getUserId(), cameraId)));
    }

    @PostMapping("/api/corporate-cameras/{cameraId}/roi-configs")
    public ResponseEntity<ApiResponse<RoiConfigResponse>> createCorporate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId,
            @Valid @RequestBody CreateRoiConfigRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        roiConfigService.createCorporate(userDetails.getUserId(), cameraId, request)));
    }

    @GetMapping("/api/corporate-cameras/{cameraId}/roi-configs")
    public ResponseEntity<ApiResponse<List<RoiConfigResponse>>> getListCorporate(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long cameraId) {
        return ResponseEntity.ok(ApiResponse.success(
                roiConfigService.getListCorporate(userDetails.getUserId(), cameraId)));
    }

    @GetMapping("/api/roi-configs/{roiConfigId}")
    public ResponseEntity<ApiResponse<RoiConfigResponse>> getOne(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roiConfigId) {
        return ResponseEntity.ok(ApiResponse.success(
                roiConfigService.getOne(userDetails.getUserId(), roiConfigId)));
    }

    @PutMapping("/api/roi-configs/{roiConfigId}")
    public ResponseEntity<ApiResponse<RoiConfigResponse>> update(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roiConfigId,
            @RequestBody UpdateRoiConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                roiConfigService.update(userDetails.getUserId(), roiConfigId, request)));
    }

    @DeleteMapping("/api/roi-configs/{roiConfigId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long roiConfigId) {
        roiConfigService.delete(userDetails.getUserId(), roiConfigId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
