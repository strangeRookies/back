package com.strange.safety.camera.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateCameraRequest {

    private String cameraLoginId;
    private String cameraPassword;

    @NotBlank(message = "RTSP URL은 필수입니다.")
    private String rtspUrl;

    private String locationDescription;
}
