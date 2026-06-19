package com.strange.safety.corporatecamera.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CorporateCameraRequest {
    private String cameraName;
    private String cameraSerialNumber;
    private String cameraLoginId;
    private String password;
    private String rtspUrl;
    private String locationDescription;
    private String assignedVideoPath;
}
