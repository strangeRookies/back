package com.strange.safety.camera.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RtspSimulationService {

    @Value("${app.simulation.mediamtx-url:rtsp://localhost:8554}")
    private String mediamtxUrl;

    private final Map<Long, Process> activeSimulations = new ConcurrentHashMap<>();

    /**
     * Starts an FFmpeg process to stream the video in a loop.
     */
    public String startSimulation(Long cameraId, String videoPath) {
        stopSimulation(cameraId); // Ensure no existing process

        String rtspUrl = mediamtxUrl + "/cam-" + cameraId;
        
        // Command: ffmpeg -re -stream_loop -1 -i {videoPath} -c copy -f rtsp {rtspUrl}
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-re",
                "-stream_loop", "-1",
                "-i", videoPath,
                "-c", "copy",
                "-f", "rtsp",
                rtspUrl
        );

        processBuilder.redirectErrorStream(true);

        try {
            Process process = processBuilder.start();
            activeSimulations.put(cameraId, process);
            log.info("Started FFmpeg simulation for camera {}. RTSP URL: {}", cameraId, rtspUrl);

            // Read output asynchronously to prevent process blocking
            new Thread(() -> {
                try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // log.debug("FFmpeg [{}]: {}", cameraId, line); // Un-comment for debugging
                    }
                } catch (IOException e) {
                    log.error("Error reading FFmpeg output for camera {}", cameraId, e);
                }
                
                log.warn("FFmpeg process for camera {} exited.", cameraId);
                activeSimulations.remove(cameraId);
            }).start();

        } catch (IOException e) {
            log.error("Failed to start FFmpeg for camera {}", cameraId, e);
            throw new RuntimeException("Failed to start RTSP simulation", e);
        }

        return rtspUrl;
    }

    /**
     * Stops the simulation for the given camera.
     */
    public void stopSimulation(Long cameraId) {
        Process process = activeSimulations.remove(cameraId);
        if (process != null && process.isAlive()) {
            log.info("Stopping FFmpeg simulation for camera {}", cameraId);
            process.destroy();
        }
    }

    @PreDestroy
    public void stopAll() {
        log.info("Stopping all active FFmpeg simulations before shutdown...");
        activeSimulations.keySet().forEach(this::stopSimulation);
    }
}
