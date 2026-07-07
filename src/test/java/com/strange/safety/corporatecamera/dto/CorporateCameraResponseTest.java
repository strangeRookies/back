package com.strange.safety.corporatecamera.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.strange.safety.camera.overlay.AiOverlayResponse;
import com.strange.safety.camera.overlay.AiOverlayStatus;
import com.strange.safety.company.entity.CompanyProfile;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CorporateCameraResponseTest {

    @Test
    void fromMergesReportedOverlayInfo() {
        CompanyProfile companyProfile = CompanyProfile.builder()
                .companyName("Test Company")
                .build();
        ReflectionTestUtils.setField(companyProfile, "id", 10L);

        CorporateCamera camera = CorporateCamera.builder()
                .companyProfile(companyProfile)
                .cameraLoginId("cam_04")
                .cameraName("Corporate Camera")
                .cameraSerialNumber("SN-CORP")
                .build();
        ReflectionTestUtils.setField(camera, "id", 20L);

        CorporateCameraResponse response = CorporateCameraResponse.from(
                camera,
                new AiOverlayResponse(
                        "cam_04",
                        "rtsp://127.0.0.1:8554/cam_04",
                        8011,
                        "http://localhost:8011/mjpeg/cam_04",
                        1234L,
                        AiOverlayStatus.RUNNING,
                        Instant.parse("2026-06-19T00:00:00Z")));

        assertThat(response.getOverlayUrl()).isEqualTo("http://localhost:8011/mjpeg/cam_04");
        assertThat(response.getOverlayStreamType()).isEqualTo("MJPEG");
        assertThat(response.isOverlayRenderedInStream()).isTrue();
    }
}
