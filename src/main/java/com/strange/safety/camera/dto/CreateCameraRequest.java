package com.strange.safety.camera.dto;


import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateCameraRequest {

    private String cameraLoginId;
    private String cameraName;
    private String cameraSerialNumber;
    private String cameraPassword;

    private String rtspUrl;

    private String locationDescription;

    private Boolean aiEnabled;
}
