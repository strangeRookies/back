package com.strange.safety.camera.dto;

import com.strange.safety.camera.entity.CameraStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCameraRequest {

    private String cameraName;
    private String cameraSerialNumber;
    private String rtspUrl;
    private CameraStatus status;
    private String locationDescription;
    private Boolean aiEnabled;
    private String assignedVideoPath;
}
