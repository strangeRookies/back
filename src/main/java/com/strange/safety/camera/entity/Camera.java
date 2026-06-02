package com.strange.safety.camera.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.facility.entity.Facility;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "camera_login_id")
    private String cameraLoginId;

    @Column(name = "camera_password_encrypted")
    private String cameraPasswordEncrypted;

    @Column(name = "rtsp_url", nullable = false)
    private String rtspUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CameraStatus status;

    @Column(name = "location_description")
    private String locationDescription;

    @Builder
    private Camera(Facility facility, String cameraLoginId, String cameraPasswordEncrypted,
                   String rtspUrl, String locationDescription) {
        this.facility = facility;
        this.cameraLoginId = cameraLoginId;
        this.cameraPasswordEncrypted = cameraPasswordEncrypted;
        this.rtspUrl = rtspUrl;
        this.locationDescription = locationDescription;
        this.status = CameraStatus.ACTIVE;
    }

    public void update(String rtspUrl, CameraStatus status, String locationDescription) {
        if (rtspUrl != null) this.rtspUrl = rtspUrl;
        if (status != null) this.status = status;
        if (locationDescription != null) this.locationDescription = locationDescription;
    }

    public void deactivate() {
        this.status = CameraStatus.INACTIVE;
    }
}
