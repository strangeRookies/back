package com.strange.safety.camera.service;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatusLog;
import com.strange.safety.camera.repository.CameraRepository;
import com.strange.safety.camera.repository.CameraStatusLogRepository;
import com.strange.safety.event.CameraStatusEventDto;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * AI 서버로부터 수신한 카메라 상태 이벤트를 처리하는 서비스.
 *
 * <p>처리 절차:
 * <ol>
 *   <li>camera_login_id로 Camera 조회</li>
 *   <li>Camera.connectionStatus 갱신 (CAMERA.status 갱신)</li>
 *   <li>CAMERA_STATUS_LOGS에 전환 이력 저장</li>
 * </ol>
 *
 * AI 서버는 상태가 변경될 때만 이벤트를 발행하므로 (23.md Section 8),
 * 수신 시마다 이전 상태와 무관하게 로그를 저장한다.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CameraStatusService {

    private static final Logger log = LoggerFactory.getLogger(CameraStatusService.class);

    private final CameraRepository cameraRepository;
    private final CameraStatusLogRepository cameraStatusLogRepository;

    /**
     * MQTT 카메라 상태 이벤트를 받아 DB를 갱신하고 이력을 저장한다.
     *
     * @param dto AI 서버가 발행한 카메라 상태 이벤트
     * @return 업데이트된 Camera (조회 실패 시 empty)
     */
    public Optional<Camera> applyStatusEvent(CameraStatusEventDto dto) {
        // 1. camera_login_id로 Camera 조회
        String cameraLoginId = dto.cameraLoginId();
        if (cameraLoginId == null || cameraLoginId.isBlank()) {
            log.warn("[CameraStatus] camera_login_id가 누락된 이벤트를 무시합니다: cameraId={}", dto.cameraId());
            return Optional.empty();
        }

        Optional<Camera> cameraOpt = cameraRepository.findFirstByCameraLoginIdOrderByIdDesc(cameraLoginId);
        if (cameraOpt.isEmpty()) {
            log.warn("[CameraStatus] camera_login_id={}에 해당하는 Camera를 찾을 수 없습니다. DB에 카메라가 등록되어 있는지 확인하세요.", cameraLoginId);
            return Optional.empty();
        }

        Camera camera = cameraOpt.get();
        CameraConnectionStatus previousStatus = camera.getConnectionStatus();
        CameraConnectionStatus currentStatus = parseConnectionStatus(dto.status());
        Instant detectedAt = dto.resolvedDetectedAt();

        // 2. Camera.connectionStatus 갱신
        camera.updateConnectionStatus(currentStatus, detectedAt);
        log.info("[CameraStatus] Camera 상태 갱신: cameraLoginId={}, {} → {}, reason={}",
                cameraLoginId, previousStatus, currentStatus, dto.reason());

        // 3. CAMERA_STATUS_LOGS에 이력 저장
        CameraStatusLog statusLog = CameraStatusLog.builder()
                .camera(camera)
                .edgeDeviceId(dto.edgeDeviceId())
                .previousStatus(previousStatus)
                .currentStatus(currentStatus)
                .reason(dto.reason())
                .detectedAt(detectedAt)
                .build();
        CameraStatusLog saved = cameraStatusLogRepository.save(statusLog);
        log.info("[CameraStatus] 상태 이력 저장 완료: logId={}, cameraLoginId={}, status={}",
                saved.getId(), cameraLoginId, currentStatus);

        return Optional.of(camera);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private CameraConnectionStatus parseConnectionStatus(String status) {
        if (status == null) {
            log.warn("[CameraStatus] status 값이 null입니다. UNKNOWN으로 처리합니다.");
            return CameraConnectionStatus.UNKNOWN;
        }
        try {
            return CameraConnectionStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("[CameraStatus] 알 수 없는 status 값: {}. UNKNOWN으로 처리합니다.", status);
            return CameraConnectionStatus.UNKNOWN;
        }
    }
}
