package com.strange.safety.corporatecamera.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class BulkCorporateCameraUploadResult {

    private int successCount;
    private int failCount;
    private List<CorporateCameraResponse> registeredCameras;
    private List<FailedRow> failedRows;

    @Getter
    @AllArgsConstructor
    public static class FailedRow {
        private int rowNumber;
        private String reason;
    }
}
