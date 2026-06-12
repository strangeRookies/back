package com.strange.safety.corporatecamera.controller;

import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.corporatecamera.dto.BulkCorporateCameraUploadResult;
import com.strange.safety.corporatecamera.dto.CorporateCameraRequest;
import com.strange.safety.corporatecamera.dto.CorporateCameraResponse;
import com.strange.safety.corporatecamera.service.CorporateCameraExcelService;
import com.strange.safety.corporatecamera.service.CorporateCameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/corporate-cameras")
public class CorporateCameraController {

    private final CorporateCameraService corporateCameraService;
    private final CorporateCameraExcelService corporateCameraExcelService;

    @PostMapping("/company/{companyProfileId}")
    public ResponseEntity<ApiResponse<CorporateCameraResponse>> registerCamera(
            @PathVariable Long companyProfileId,
            @RequestBody CorporateCameraRequest request) {
        CorporateCameraResponse response = corporateCameraService.register(companyProfileId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping(value = "/company/{companyProfileId}/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BulkCorporateCameraUploadResult>> bulkUpload(
            @PathVariable Long companyProfileId,
            @RequestParam("file") MultipartFile file) {
        BulkCorporateCameraUploadResult result = corporateCameraExcelService.bulkUpload(companyProfileId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping("/company/{companyProfileId}")
    public ResponseEntity<ApiResponse<List<CorporateCameraResponse>>> getCamerasByCompany(
            @PathVariable Long companyProfileId) {
        return ResponseEntity.ok(ApiResponse.success(corporateCameraService.getCamerasByCompany(companyProfileId)));
    }

    @DeleteMapping("/{cameraId}")
    public ResponseEntity<ApiResponse<Void>> deleteCamera(@PathVariable Long cameraId) {
        corporateCameraService.deleteCamera(cameraId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
