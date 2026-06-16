package com.strange.safety.camera.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.facility.entity.Facility;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.strange.safety.alert.entity.AlertEvent;

@Entity
@Table(name = "cameras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Camera extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camera_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AlertEvent> alertEvents = new ArrayList<>();

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoiConfig> roiConfigs = new ArrayList<>();

    @OneToMany(mappedBy = "camera", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CameraStatusLog> statusLogs = new ArrayList<>();

    @Column(name = "camera_login_id")
    private String cameraLoginId;

    @Column(name = "camera_name")
    private String cameraName;

    @Column(name = "camera_sn")
    private String cameraSerialNumber;

    @Column(name = "camera_password_encrypted")
    private String cameraPasswordEncrypted;

    @Column(name = "rtsp_url")
    private String rtspUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraStatus status;

    @Column(name = "location_description")
    private String locationDescription;

    @Column(name = "ai_enabled", nullable = false, columnDefinition = "boolean default true")
    private boolean aiEnabled;

    /**
     * AI Edge 서버가 MQTT로 보고하는 실시간 RTSP 연결 상태.
     * 관리자 운영 상태인 {@link CameraStatus}와 별개로 관리된다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20, columnDefinition = "varchar(20) default 'UNKNOWN'")
    private CameraConnectionStatus connectionStatus;

    /** AI 서버가 마지막으로 연결 상태를 보고한 시각 (UTC) */
    @Column(name = "last_connection_report_at")
    private Instant lastConnectionReportAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, columnDefinition = "varchar(20) default 'REAL_RTSP'")
    private CameraSourceType sourceType;

    @Column(name = "assigned_video_path")
    private String assignedVideoPath;

    @Builder
    private Camera(Facility facility, String cameraLoginId, String cameraName, String cameraSerialNumber,
                   String cameraPasswordEncrypted, String rtspUrl, String locationDescription, Boolean aiEnabled,
                   CameraSourceType sourceType, String assignedVideoPath) {
        this.facility = facility;
        this.cameraLoginId = cameraLoginId;
        this.cameraName = cameraName;
        this.cameraSerialNumber = cameraSerialNumber;
        this.cameraPasswordEncrypted = cameraPasswordEncrypted;
        this.rtspUrl = rtspUrl;
        this.locationDescription = locationDescription;
        this.status = CameraStatus.ACTIVE;
        this.connectionStatus = CameraConnectionStatus.UNKNOWN;
        this.aiEnabled = aiEnabled != null ? aiEnabled : true;
        this.sourceType = sourceType != null ? sourceType : CameraSourceType.REAL_RTSP;
        this.assignedVideoPath = assignedVideoPath;
    }

    public void update(String cameraName, String cameraSerialNumber, String rtspUrl, CameraStatus status, String locationDescription, Boolean aiEnabled, CameraSourceType sourceType, String assignedVideoPath) {
        if (cameraName != null) this.cameraName = cameraName;
        if (cameraSerialNumber != null) this.cameraSerialNumber = cameraSerialNumber;
        if (rtspUrl != null) this.rtspUrl = rtspUrl;
        if (status != null) this.status = status;
        if (locationDescription != null) this.locationDescription = locationDescription;
        if (aiEnabled != null) this.aiEnabled = aiEnabled;
        if (sourceType != null) this.sourceType = sourceType;
        if (assignedVideoPath != null) this.assignedVideoPath = assignedVideoPath;
    }

    /**
     * AI 서버로부터 수신한 MQTT 카메라 상태 이벤트로 실시간 연결 상태를 갱신한다.
     *
     * @param connectionStatus AI 서버가 보고한 연결 상태
     * @param reportedAt       상태가 감지된 시각 (AI 서버 기준, UTC)
     */
    public void updateConnectionStatus(CameraConnectionStatus connectionStatus, Instant reportedAt) {
        this.connectionStatus = connectionStatus;
        this.lastConnectionReportAt = reportedAt != null ? reportedAt : Instant.now();
    }

    public void deactivate() {
        this.status = CameraStatus.INACTIVE;
    }
}
