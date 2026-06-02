package com.strange.safety.facility.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.facility.dto.CreateFacilityRequest;
import com.strange.safety.facility.dto.FacilityResponse;
import com.strange.safety.facility.dto.UpdateFacilityRequest;
import com.strange.safety.facility.service.FacilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/facilities")
@RequiredArgsConstructor
public class FacilityController {

    private final FacilityService facilityService;

    @PostMapping
    public ResponseEntity<ApiResponse<FacilityResponse>> createFacility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateFacilityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        facilityService.createFacility(userDetails.getUserId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<FacilityResponse>>> getFacilities(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                facilityService.getFacilities(userDetails.getUserId(), pageable)));
    }

    @PutMapping("/{facilityId}")
    public ResponseEntity<ApiResponse<FacilityResponse>> updateFacility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            @Valid @RequestBody UpdateFacilityRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                facilityService.updateFacility(userDetails.getUserId(), facilityId, request)));
    }

    @DeleteMapping("/{facilityId}")
    public ResponseEntity<ApiResponse<Void>> deleteFacility(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId) {
        facilityService.deleteFacility(userDetails.getUserId(), facilityId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
