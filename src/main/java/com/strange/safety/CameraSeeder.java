package com.strange.safety;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.facility.entity.Facility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class CameraSeeder implements CommandLineRunner {

    private final CameraRepository cameraRepository;

    @Override
    public void run(String... args) throws Exception {
        log.info("Executing CameraSeeder...");
        Optional<Camera> cam01Opt = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc("cam_01");
        if (cam01Opt.isEmpty()) {
            log.warn("cam_01 not found. Cannot seed cam_02~cam_04 because facility is unknown.");
            return;
        }

        Camera cam01 = cam01Opt.get();
        if ("rtsp://localhost:8554/cam1".equals(cam01.getRtspUrl())) {
            log.info("Updating cam_01 rtspUrl from cam1 to cam_01 to match naming convention.");
            cam01.update(null, null, "rtsp://localhost:8554/cam_01", null, null, null, null);
            cameraRepository.save(cam01);
        }
        Facility facility = cam01.getFacility();

        String[] camIds = {"cam_02", "cam_03", "cam_04"};
        for (String camId : camIds) {
            Optional<Camera> camOpt = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc(camId);
            if (camOpt.isEmpty()) {
                log.info("Seeding camera: {}", camId);
                Camera camera = Camera.builder()
                        .facility(facility)
                        .cameraLoginId(camId)
                        .cameraName(camId)
                        .cameraSerialNumber("SN-12345-" + camId)
                        .rtspUrl("rtsp://localhost:8554/" + camId)
                        .locationDescription("Auto-seeded camera " + camId)
                        .aiEnabled(true)
                        .assignedVideoPath("video_pool/dummy.mp4")
                        .build();
                camera.updateConnectionStatus(CameraConnectionStatus.CONNECTED, Instant.now());
                cameraRepository.save(camera);
                log.info("Successfully seeded camera: {}", camId);
            } else {
                log.info("Camera {} already exists.", camId);
                Camera camera = camOpt.get();
                String expectedUrl = "rtsp://localhost:8554/" + camId;
                if (!expectedUrl.equals(camera.getRtspUrl())) {
                    log.info("Updating {} rtspUrl from {} to {}.", camId, camera.getRtspUrl(), expectedUrl);
                    camera.update(null, null, expectedUrl, null, null, null, null);
                    cameraRepository.save(camera);
                }
            }
        }
    }
}
