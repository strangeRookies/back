package com.strange.safety.alert.controller;

import com.strange.safety.alert.dto.AlertEventDetailResponse;
import com.strange.safety.alert.dto.AlertEventResponse;
import com.strange.safety.alert.dto.AlertStatsResponse;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import com.strange.safety.alert.service.AlertEventService;
import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class AlertEventController {

    private final AlertEventService alertEventService;

    @GetMapping("/api/facilities/{facilityId}/alert-events")
    public ResponseEntity<ApiResponse<Page<AlertEventResponse>>> getList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getList(userDetails.getUserId(), facilityId,
                        severity, status, dateFrom, dateTo, pageable)));
    }

    @GetMapping("/api/alert-events/{alertEventId}")
    public ResponseEntity<ApiResponse<AlertEventDetailResponse>> getDetail(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertEventId) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getDetail(userDetails.getUserId(), alertEventId)));
    }

    @GetMapping("/api/facilities/{facilityId}/alert-events/recent")
    public ResponseEntity<ApiResponse<List<AlertEventResponse>>> getRecent(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getRecent(userDetails.getUserId(), facilityId)));
    }

    @PatchMapping("/api/alert-events/{alertEventId}/acknowledge")
    public ResponseEntity<ApiResponse<AlertEventResponse>> acknowledge(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long alertEventId) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.acknowledge(userDetails.getUserId(), alertEventId)));
    }

    @GetMapping("/api/facilities/{facilityId}/alert-events/stats")
    public ResponseEntity<ApiResponse<AlertStatsResponse>> getStats(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long facilityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getStats(userDetails.getUserId(), facilityId, dateFrom, dateTo)));
    }
}
