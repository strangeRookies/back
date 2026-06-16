package com.strange.safety.alert.entity;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.scenario.entity.Scenario;
import com.strange.safety.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "alert_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertEvent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "alert_event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @OneToMany(mappedBy = "alertEvent", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Snapshot> snapshots = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "confidence_score", nullable = false)
    private Float confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AlertStatus status;

    @Column(name = "keypoint_data", columnDefinition = "text")
    private String keypointData;

    @Column(name = "bounding_box_data", columnDefinition = "text")
    private String boundingBoxData;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acknowledged_by")
    private User acknowledgedBy;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Builder
    private AlertEvent(Camera camera, Scenario scenario, Float confidenceScore,
                       AlertSeverity severity, String keypointData,
                       String boundingBoxData, LocalDateTime detectedAt) {
        this.camera = camera;
        this.scenario = scenario;
        this.confidenceScore = confidenceScore;
        this.severity = severity;
        this.status = AlertStatus.PENDING;
        this.keypointData = keypointData;
        this.boundingBoxData = boundingBoxData;
        this.detectedAt = detectedAt;
    }

    public void acknowledge(User user) {
        this.status = AlertStatus.CONFIRMED;
        this.acknowledgedBy = user;
        this.acknowledgedAt = LocalDateTime.now();
        this.resolvedAt = LocalDateTime.now();
    }
}
