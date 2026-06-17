package com.strange.safety.camera.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCameraStatsResponse {
    private long totalCount;
    private long connectedCount;
}
