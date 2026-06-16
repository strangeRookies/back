package com.strange.safety.camera.controller;

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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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

    @GetMapping("/facilities/{facilityId}/cameras")
    public ResponseEntity<ApiResponse<List<CameraResponse>>> getCamerasByFacility(
            @PathVariable Long facilityId) {
        return ResponseEntity.ok(ApiResponse.success(cameraService.getCamerasForAdmin(facilityId)));
    }
}
