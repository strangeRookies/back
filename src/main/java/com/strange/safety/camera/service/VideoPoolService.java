package com.strange.safety.camera.service;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class VideoPoolService {

    private final CameraRepository cameraRepository;

    @Value("${app.simulation.video-pool-path:video_pool}")
    private String videoPoolPath;

    /**
     * Assigns the least-used mp4 video from the video pool.
     */
    public String assignLeastUsedVideo() {
        List<Path> availableVideos = getAvailableVideos();
        if (availableVideos.isEmpty()) {
            throw new RuntimeException("No video files found in video pool: " + videoPoolPath);
        }

        List<String> assignedPaths = cameraRepository.findAll().stream()
                .map(Camera::getAssignedVideoPath)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<String, Long> usageCount = assignedPaths.stream()
                .collect(Collectors.groupingBy(path -> path, Collectors.counting()));

        // Randomize to break ties randomly
        Collections.shuffle(availableVideos);

        String selectedVideo = availableVideos.stream()
                .map(Path::toString)
                .min(Comparator.comparingLong(path -> usageCount.getOrDefault(path, 0L)))
                .orElse(availableVideos.get(0).toString());

        log.info("Assigned video: {} (Current usage count: {})", selectedVideo, usageCount.getOrDefault(selectedVideo, 0L));
        return selectedVideo;
    }

    private List<Path> getAvailableVideos() {
        Path poolDir = Paths.get(videoPoolPath);
        if (!Files.exists(poolDir)) {
            log.warn("Video pool directory not found: {}. Creating it.", videoPoolPath);
            try {
                Files.createDirectories(poolDir);
            } catch (IOException e) {
                log.error("Failed to create video pool directory", e);
            }
        }

        try (Stream<Path> paths = Files.walk(poolDir)) {
            List<Path> mp4Files = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase().endsWith(".mp4"))
                    .collect(Collectors.toList());

            if (mp4Files.isEmpty()) {
                // Fallback to sample_videos if available
                Path sampleDir = Paths.get("sample_videos");
                if (Files.exists(sampleDir)) {
                    try (Stream<Path> samplePaths = Files.walk(sampleDir)) {
                        return samplePaths
                                .filter(Files::isRegularFile)
                                .filter(path -> path.toString().toLowerCase().endsWith(".mp4"))
                                .collect(Collectors.toList());
                    }
                }
            }

            return mp4Files;
        } catch (IOException e) {
            log.error("Error reading video pool directory", e);
            return Collections.emptyList();
        }
    }
}
