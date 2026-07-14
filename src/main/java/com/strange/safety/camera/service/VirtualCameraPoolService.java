package com.strange.safety.camera.service;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.repository.CameraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SIMULATED_RTSP 카메라에 video_pool mp4를 배정한다.
 *
 * <p>경로 비교는 절대/상대 혼용을 허용하도록 basename 기준으로 사용량을 집계하고,
 * DB에는 이식 가능한 {@code video_pool/<filename>} 상대 경로를 저장한다.
 * pool이 비어 있으면 null을 반환해 AI worker가 cameraLoginId stable index로
 * 자체 pool을 다양하게 매핑할 수 있게 한다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualCameraPoolService {

    private final CameraRepository cameraRepository;

    @Value("${simulation.video.pool-path:video_pool}")
    private String videoPoolPath;

    public String assignVideo() {
        List<File> poolFiles = listPoolMp4Files();
        if (poolFiles.isEmpty()) {
            log.warn(
                    "Video pool has no mp4 files at {}. Returning null so AI can diversify from its own pool.",
                    new File(videoPoolPath).getAbsolutePath()
            );
            return null;
        }

        Map<String, Long> usageCount = buildUsageCount(poolFiles);
        List<File> shuffled = new ArrayList<>(poolFiles);
        Collections.shuffle(shuffled);

        File selected = shuffled.get(0);
        long minCount = Long.MAX_VALUE;
        for (File file : shuffled) {
            long count = usageCount.getOrDefault(usageKey(file.getName()), 0L);
            if (count < minCount) {
                minCount = count;
                selected = file;
            }
        }

        String relativePath = toStoredRelativePath(selected.getName());
        log.info(
                "Assigned video {} (usageCount={}, poolSize={})",
                relativePath,
                minCount,
                poolFiles.size()
        );
        return relativePath;
    }

    /**
     * 동일 assignedVideoPath를 공유하는 카메라가 있으면 pool 기준으로 재배정한다.
     * pool에 영상이 1개 이하이면 공유 경로를 null로 비워 AI-side diversification을 허용한다.
     */
    @Transactional
    public int rebalanceDuplicateAssignments() {
        List<Camera> cameras = cameraRepository.findAll();
        Map<String, List<Camera>> byUsageKey = new LinkedHashMap<>();
        for (Camera camera : cameras) {
            String key = usageKey(camera.getAssignedVideoPath());
            if (key == null) {
                continue;
            }
            byUsageKey.computeIfAbsent(key, ignored -> new ArrayList<>()).add(camera);
        }

        List<File> poolFiles = listPoolMp4Files();
        int changed = 0;

        for (Map.Entry<String, List<Camera>> entry : byUsageKey.entrySet()) {
            List<Camera> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }

            log.warn(
                    "Detected {} cameras sharing assigned video '{}': {}",
                    group.size(),
                    entry.getKey(),
                    group.stream().map(Camera::getCameraLoginId).collect(Collectors.joining(", "))
            );

            if (poolFiles.size() <= 1) {
                for (Camera camera : group) {
                    if (camera.getAssignedVideoPath() != null) {
                        camera.clearAssignedVideoPath();
                        cameraRepository.save(camera);
                        changed++;
                        log.info(
                                "Cleared shared assignedVideoPath for cameraLoginId={} (poolSize={}) so AI can diversify",
                                camera.getCameraLoginId(),
                                poolFiles.size()
                        );
                    }
                }
                continue;
            }

            // Exclude this shared group from temporary usage so reassignment spreads cleanly.
            Map<String, Long> usageCount = buildUsageCount(poolFiles);
            long shared = group.size();
            usageCount.computeIfPresent(entry.getKey(), (k, v) -> Math.max(0L, v - shared));

            for (Camera camera : group) {
                File selected = pickLeastUsed(poolFiles, usageCount);
                String relativePath = toStoredRelativePath(selected.getName());
                usageCount.merge(usageKey(selected.getName()), 1L, Long::sum);
                if (!Objects.equals(camera.getAssignedVideoPath(), relativePath)) {
                    camera.update(null, null, null, null, null, null, relativePath);
                    cameraRepository.save(camera);
                    changed++;
                    log.info(
                            "Rebalanced cameraLoginId={} -> assignedVideoPath={}",
                            camera.getCameraLoginId(),
                            relativePath
                    );
                }
            }
        }

        if (changed > 0) {
            log.info("VirtualCameraPool rebalance finished: {} camera assignment(s) updated", changed);
        }
        return changed;
    }

    private File pickLeastUsed(List<File> poolFiles, Map<String, Long> usageCount) {
        List<File> shuffled = new ArrayList<>(poolFiles);
        Collections.shuffle(shuffled);
        File selected = shuffled.get(0);
        long minCount = Long.MAX_VALUE;
        for (File file : shuffled) {
            long count = usageCount.getOrDefault(usageKey(file.getName()), 0L);
            if (count < minCount) {
                minCount = count;
                selected = file;
            }
        }
        return selected;
    }

    private Map<String, Long> buildUsageCount(List<File> poolFiles) {
        Map<String, Long> usageCount = new HashMap<>();
        for (File file : poolFiles) {
            usageCount.put(usageKey(file.getName()), 0L);
        }
        for (Camera camera : cameraRepository.findAll()) {
            String key = usageKey(camera.getAssignedVideoPath());
            if (key != null && usageCount.containsKey(key)) {
                usageCount.put(key, usageCount.get(key) + 1);
            }
        }
        return usageCount;
    }

    private List<File> listPoolMp4Files() {
        File poolDir = new File(videoPoolPath);
        if (!poolDir.exists() || !poolDir.isDirectory()) {
            log.warn("Video pool directory not found: {}", poolDir.getAbsolutePath());
            return List.of();
        }
        File[] files = poolDir.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".mp4"));
        if (files == null || files.length == 0) {
            return List.of();
        }
        return Arrays.stream(files).sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName())).toList();
    }

    private String toStoredRelativePath(String fileName) {
        String pool = videoPoolPath == null ? "video_pool" : videoPoolPath.replace('\\', '/');
        while (pool.endsWith("/")) {
            pool = pool.substring(0, pool.length() - 1);
        }
        if (pool.isBlank()) {
            pool = "video_pool";
        }
        return pool + "/" + fileName;
    }

    static String usageKey(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.replace('\\', '/').trim();
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        if (name.isBlank()) {
            return null;
        }
        return name.toLowerCase(Locale.ROOT);
    }
}
