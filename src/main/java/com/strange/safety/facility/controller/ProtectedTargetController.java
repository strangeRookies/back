package com.strange.safety.facility.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.facility.dto.CreateProtectedTargetRequest;
import com.strange.safety.facility.dto.ProtectedTargetResponse;
import com.strange.safety.facility.dto.UpdateProtectedTargetRequest;
import com.strange.safety.facility.service.ProtectedTargetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class ProtectedTargetController {

    private final ProtectedTargetService protectedTargetService;

    @PostMapping("/api/facilities/{facilityId}/protected-targets")
    public ResponseEntity<ApiResponse<ProtectedTargetResponse>> createProtectedTarget(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            @Valid @RequestBody CreateProtectedTargetRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(protectedTargetService.createProtectedTarget(
                        userDetails.getUserId(), facilityId, request)));
    }

    @GetMapping("/api/facilities/{facilityId}/protected-targets")
    public ResponseEntity<ApiResponse<Page<ProtectedTargetResponse>>> getProtectedTargets(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                protectedTargetService.getProtectedTargets(
                        userDetails.getUserId(), facilityId, pageable)));
    }

    @GetMapping("/api/protected-targets/{targetId}")
    public ResponseEntity<ApiResponse<ProtectedTargetResponse>> getProtectedTarget(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long targetId) {
        return ResponseEntity.ok(ApiResponse.success(
                protectedTargetService.getProtectedTarget(
                        userDetails.getUserId(), targetId)));
    }

    @PutMapping("/api/protected-targets/{targetId}")
    public ResponseEntity<ApiResponse<ProtectedTargetResponse>> updateProtectedTarget(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long targetId,
            @Valid @RequestBody UpdateProtectedTargetRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                protectedTargetService.updateProtectedTarget(
                        userDetails.getUserId(), targetId, request)));
    }

    @DeleteMapping("/api/protected-targets/{targetId}")
    public ResponseEntity<ApiResponse<Void>> deleteProtectedTarget(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long targetId) {
        protectedTargetService.deleteProtectedTarget(userDetails.getUserId(), targetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
