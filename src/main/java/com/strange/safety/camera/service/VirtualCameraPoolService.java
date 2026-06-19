package com.strange.safety.camera.service;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualCameraPoolService {

    private final CameraRepository cameraRepository;

    @Value("${simulation.video.pool-path:video_pool}")
    private String videoPoolPath;

    public String assignVideo() {
        File poolDir = new File(videoPoolPath);
        if (!poolDir.exists() || !poolDir.isDirectory()) {
            log.warn("Video pool directory not found: {}. Fallback to dummy path.", poolDir.getAbsolutePath());
            return "video_pool/dummy.mp4";
        }

        File[] files = poolDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files == null || files.length == 0) {
            log.warn("No mp4 files found in {}. Fallback to dummy path.", poolDir.getAbsolutePath());
            return "video_pool/dummy.mp4";
        }

        List<String> availableVideos = Arrays.stream(files)
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

        List<Camera> allCameras = cameraRepository.findAll();

        Map<String, Long> usageCount = new HashMap<>();
        for (String v : availableVideos) {
            usageCount.put(v, 0L);
        }

        for (Camera c : allCameras) {
            String path = c.getAssignedVideoPath();
            if (path != null && usageCount.containsKey(path)) {
                usageCount.put(path, usageCount.get(path) + 1);
            }
        }

        // Find the video with the minimum usage count
        String selectedVideo = availableVideos.get(0);
        long minCount = Long.MAX_VALUE;

        // Randomize the order to avoid picking the same one if counts are equal
        Collections.shuffle(availableVideos);

        for (String v : availableVideos) {
            long count = usageCount.get(v);
            if (count < minCount) {
                minCount = count;
                selectedVideo = v;
            }
        }

        log.info("Assigned video {} with usage count {}", selectedVideo, minCount);
        return selectedVideo;
    }
}
