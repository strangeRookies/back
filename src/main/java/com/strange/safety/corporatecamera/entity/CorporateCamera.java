package com.strange.safety.corporatecamera.entity;

import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraSourceType;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.company.entity.CompanyProfile;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "corporate_cameras")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CorporateCamera extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "camera_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_profile_id", nullable = false)
    private CompanyProfile companyProfile;

    @Column(name = "camera_name")
    private String cameraName;

    @Column(name = "camera_sn")
    private String cameraSerialNumber;

    @Column(name = "camera_login_id")
    private String cameraLoginId;

    @Column(name = "camera_password_encrypted")
    private String cameraPasswordEncrypted;

    @Column(name = "rtsp_url")
    private String rtspUrl;

    @Column(name = "location_description")
    private String locationDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_status", nullable = false, length = 20, columnDefinition = "varchar(20) default 'UNKNOWN'")
    private CameraConnectionStatus connectionStatus;

    @Column(name = "last_connection_report_at")
    private Instant lastConnectionReportAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20, columnDefinition = "varchar(20) default 'REAL_RTSP'")
    private CameraSourceType sourceType;

    @Column(name = "assigned_video_path")
    private String assignedVideoPath;

    @Builder
    private CorporateCamera(CompanyProfile companyProfile, String cameraName, String cameraSerialNumber,
                            String cameraLoginId, String cameraPasswordEncrypted, String rtspUrl,
                            String locationDescription, CameraSourceType sourceType, String assignedVideoPath) {
        this.companyProfile = companyProfile;
        this.cameraName = cameraName;
        this.cameraSerialNumber = cameraSerialNumber;
        this.cameraLoginId = cameraLoginId;
        this.cameraPasswordEncrypted = cameraPasswordEncrypted;
        this.rtspUrl = rtspUrl;
        this.locationDescription = locationDescription;
        this.status = CameraStatus.ACTIVE;
        this.connectionStatus = CameraConnectionStatus.UNKNOWN;
        this.sourceType = sourceType != null ? sourceType : CameraSourceType.REAL_RTSP;
        this.assignedVideoPath = assignedVideoPath;
    }
}
