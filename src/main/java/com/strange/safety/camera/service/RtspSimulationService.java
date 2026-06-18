package com.strange.safety.camera.service;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraSourceType;
import com.strange.safety.camera.repository.CameraRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class RtspSimulationService {

    private final CameraRepository cameraRepository;
    private final ObjectProvider<MqttClient> mqttClientProvider;

    @Value("${simulation.rtsp.base-url:rtsp://localhost:8554}")
    private String rtspBaseUrl;

    @Value("${mqtt.camera-status-topic:safety/cameras/status}")
    private String cameraStatusTopic;

    private final Map<String, Process> activeProcesses = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("Starting RTSP simulations for existing simulated cameras...");
        List<Camera> cameras = cameraRepository.findAll();
        for (Camera c : cameras) {
            if (c.getSourceType() == CameraSourceType.SIMULATED_RTSP && c.getAssignedVideoPath() != null) {
                startSimulation(c.getCameraLoginId(), c.getAssignedVideoPath(), c.getRtspUrl());
            }
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("Stopping all RTSP simulation processes...");
        activeProcesses.values().forEach(p -> {
            p.destroy();
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        activeProcesses.clear();
    }

    public String generateRtspUrl(String cameraLoginId) {
        return rtspBaseUrl + "/" + cameraLoginId;
    }

    public void startSimulation(String cameraLoginId, String videoPath, String rtspUrl) {
        stopSimulation(cameraLoginId); // Stop existing if any

        if (videoPath == null) {
            log.warn("Simulation video path is null for camera: {}. Skipping.", cameraLoginId);
            return;
        }

        java.io.File videoFile = new java.io.File(videoPath);
        if (!videoFile.exists()) {
            log.warn("Simulation video file not found locally: {}. Skipping local simulation start for camera: {}.", videoPath, cameraLoginId);
            return;
        }

        try {
            log.info("Starting FFmpeg simulation for camera: {}, video: {}, to: {}", cameraLoginId, videoPath, rtspUrl);
            ProcessBuilder pb = new ProcessBuilder(
                    "ffmpeg", "-re", "-stream_loop", "-1", "-i", videoPath,
                    "-c:v", "libx264", "-preset", "veryfast", "-tune", "zerolatency",
                    "-f", "rtsp", rtspUrl
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            activeProcesses.put(cameraLoginId, process);

            // Publish CONNECTED status
            publishStatus(cameraLoginId, CameraConnectionStatus.CONNECTED);

            // Monitor process in background
            Thread monitorThread = new Thread(() -> {
                try {
                    int exitCode = process.waitFor();
                    log.warn("FFmpeg process for camera {} exited with code {}", cameraLoginId, exitCode);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("FFmpeg monitor thread for camera {} interrupted", cameraLoginId);
                } finally {
                    activeProcesses.remove(cameraLoginId);
                    publishStatus(cameraLoginId, CameraConnectionStatus.ERROR);
                }
            });
            monitorThread.setDaemon(true);
            monitorThread.start();

        } catch (Exception e) {
            log.error("Failed to start FFmpeg for camera {}", cameraLoginId, e);
            publishStatus(cameraLoginId, CameraConnectionStatus.ERROR);
        }
    }

    public void stopSimulation(String cameraLoginId) {
        Process process = activeProcesses.remove(cameraLoginId);
        if (process != null) {
            log.info("Stopping FFmpeg simulation for camera: {}", cameraLoginId);
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            publishStatus(cameraLoginId, CameraConnectionStatus.DISCONNECTED);
        }
    }

    private void publishStatus(String cameraLoginId, CameraConnectionStatus status) {
        try {
            MqttClient mqttClient = mqttClientProvider.getIfAvailable();
            if (mqttClient == null) {
                log.debug("No raw MqttClient bean available; skipping simulation status publish for camera {}", cameraLoginId);
                return;
            }
            if (mqttClient.isConnected()) {
                String payload = String.format(
                        "{\"camera_id\":\"%s\", \"camera_login_id\":\"%s\", \"status\":\"%s\", \"timestamp\":\"%s\"}",
                        cameraLoginId, cameraLoginId, status.name(), Instant.now().toString());
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                mqttClient.publish(cameraStatusTopic, message);
                log.info("Published simulation status {} for camera {}", status, cameraLoginId);
            }
        } catch (Exception e) {
            log.error("Failed to publish camera status for {}", cameraLoginId, e);
        }
    }
}
