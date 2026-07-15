package com.strange.safety.camera.entity;
import com.strange.safety.corporatecamera.entity.CorporateCamera;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "camera_status_logs",
        indexes = {
                @Index(name = "idx_csl_camera_id", columnList = "camera_id"),
                @Index(name = "idx_csl_corporate_camera_id", columnList = "corporate_camera_id"),
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
    @JoinColumn(name = "camera_id")
    private Camera camera;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corporate_camera_id")
    private CorporateCamera corporateCamera;

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
    private CameraStatusLog(Camera camera, CorporateCamera corporateCamera, String edgeDeviceId,
                            CameraConnectionStatus previousStatus,
                            CameraConnectionStatus currentStatus,
                            String reason, Instant detectedAt) {
        if ((camera == null) == (corporateCamera == null)) {
            throw new IllegalArgumentException("Exactly one camera source is required");
        }
        this.camera = camera;
        this.corporateCamera = corporateCamera;
        this.edgeDeviceId = edgeDeviceId;
        this.previousStatus = previousStatus;
        this.currentStatus = currentStatus;
        this.reason = reason;
        this.detectedAt = detectedAt != null ? detectedAt : Instant.now();
    }
}
