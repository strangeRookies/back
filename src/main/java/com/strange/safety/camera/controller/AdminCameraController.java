package com.strange.safety.camera.controller;

import com.strange.safety.alert.dto.AdminFalsePositiveRateResponse;
import com.strange.safety.alert.dto.AlertEventResponse;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import com.strange.safety.alert.service.AlertEventService;
import com.strange.safety.camera.dto.AdminCameraStatsResponse;
import com.strange.safety.camera.dto.BulkCameraUploadResult;
import com.strange.safety.camera.dto.CameraResponse;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.camera.service.CameraExcelService;
import com.strange.safety.camera.service.CameraService;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.corporatecamera.repository.CorporateCameraRepository;
import com.strange.safety.facility.dto.AdminFacilityResponse;
import com.strange.safety.facility.service.FacilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminCameraController {

    private final CameraExcelService cameraExcelService;
    private final CameraService cameraService;
    private final FacilityService facilityService;
    private final CameraRepository cameraRepository;
    private final CorporateCameraRepository corporateCameraRepository;
    private final AlertEventService alertEventService;

    @PostMapping(value = "/cameras/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkCameraUploadResult>> bulkUpload(
            @RequestParam("facilityId") Long facilityId,
            @RequestParam("file") MultipartFile file) {
        BulkCameraUploadResult result = cameraExcelService.bulkUpload(facilityId, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result));
    }

    @GetMapping("/individual-facilities")
    public ResponseEntity<ApiResponse<List<AdminFacilityResponse>>> getAllIndividualFacilities() {
        return ResponseEntity.ok(ApiResponse.success(facilityService.getAllIndividualFacilitiesForAdmin()));
    }

    @GetMapping("/cameras/stats")
    public ResponseEntity<ApiResponse<AdminCameraStatsResponse>> getCameraStats() {
        long totalCount = cameraRepository.count() + corporateCameraRepository.count();
        long connectedCount = cameraRepository.countByConnectionStatus(CameraConnectionStatus.CONNECTED)
                + corporateCameraRepository.countByConnectionStatus(CameraConnectionStatus.CONNECTED);
        return ResponseEntity.ok(ApiResponse.success(
                AdminCameraStatsResponse.builder()
                        .totalCount(totalCount)
                        .connectedCount(connectedCount)
                        .build()));
    }

    @GetMapping("/alert-events/today-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTodayAlertCount() {
        long count = alertEventService.countAllAlertsToday();
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", count)));
    }

    @GetMapping("/alert-events/false-positive-rate")
    public ResponseEntity<ApiResponse<AdminFalsePositiveRateResponse>> getFalsePositiveRate() {
        return ResponseEntity.ok(ApiResponse.success(alertEventService.getFalsePositiveRate()));
    }

    @GetMapping("/facilities/{facilityId}/cameras")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getCamerasByFacility(
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getCamerasForAdmin(facilityId)));
    }

    /**
     * 관리자 테스트 모드(개인용 대시보드 미리보기) 전용 조회 API.
     * 실제 소유자가 아니어도 ADMIN role이면 임의의 facility/company 이벤트를 조회할 수 있다.
     */
    @GetMapping("/facilities/{facilityId}/alert-events/recent")
    public ResponseEntity<ApiResponse<List<AlertEventResponse>>> getRecentAlertEventsForFacility(
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success(alertEventService.getRecentForAdmin(facilityId, false)));
    }

    @GetMapping("/companies/{companyProfileId}/alert-events/recent")
    public ResponseEntity<ApiResponse<List<AlertEventResponse>>> getRecentAlertEventsForCompany(
            @PathVariable Long companyProfileId) {
        return ResponseEntity.ok(ApiResponse.success(alertEventService.getRecentForAdmin(companyProfileId, true)));
    }

    @GetMapping("/facilities/{facilityId}/alert-events")
    public ResponseEntity<ApiResponse<Page<AlertEventResponse>>> getAlertEventsForFacility(
            @PathVariable Long facilityId,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getListForAdmin(facilityId, false,
                        severity, status, dateFrom, dateTo, cameraId, keyword, pageable)));
    }

    @GetMapping("/companies/{companyProfileId}/alert-events")
    public ResponseEntity<ApiResponse<Page<AlertEventResponse>>> getAlertEventsForCompany(
            @PathVariable Long companyProfileId,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) Long cameraId,
            @RequestParam(required = false) String keyword,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                alertEventService.getListForAdmin(companyProfileId, true,
                        severity, status, dateFrom, dateTo, cameraId, keyword, pageable)));
    }
}
