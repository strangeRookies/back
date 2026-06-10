package com.strange.safety.camera.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * AI 서버가 MQTT로 보고한 카메라 연결 상태 변경 이력을 저장하는 엔티티.
 * 23.md CAMERA_STATUS_LOGS 테이블 스펙에 대응한다.
 *
 * 상태가 변경될 때만 기록된다 (23.md Section 8: "상태가 변경되었을 때만 MQTT로 발행").
 */
@Entity
@Table(name = "camera_status_logs",
        indexes = {
                @Index(name = "idx_csl_camera_id", columnList = "camera_id"),
                @Index(name = "idx_csl_detected_at", columnList = "detected_at"),
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CameraStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    /** AI 서버가 보고한 Edge 디바이스 ID */
    @Column(name = "edge_device_id", length = 100)
    private String edgeDeviceId;

    /** 전환 이전 상태 (null이면 최초 보고) */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 20)
    private CameraConnectionStatus previousStatus;

    /** 전환 이후(현재) 상태 */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 20)
    private CameraConnectionStatus currentStatus;

    /**
     * 상태 변경 사유.
     * e.g. RTSP_TIMEOUT, RECONNECT_ATTEMPT, AUTH_FAILED
     */
    @Column(name = "reason", length = 200)
    private String reason;

    /** AI 서버가 상태를 감지한 시각 (UTC) */
    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    /** 백엔드가 이 로그를 DB에 저장한 시각 */
    @CreatedDate
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @Builder
    private CameraStatusLog(Camera camera, String edgeDeviceId,
                            CameraConnectionStatus previousStatus,
                            CameraConnectionStatus currentStatus,
                            String reason, Instant detectedAt) {
        this.camera = camera;
        this.edgeDeviceId = edgeDeviceId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.reason = reason;
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
    }
}
