package com.strange.safety.camera.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.scenario.entity.Scenario;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roi_configs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoiConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "roi_config_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", nullable = false)
    private Scenario scenario;

    @Column(name = "polygon_points", nullable = false, columnDefinition = "text")
    private String polygonPoints;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    private RoiConfig(Camera camera, Scenario scenario, String polygonPoints) {
        this.camera = camera;
        this.scenario = scenario;
        this.polygonPoints = polygonPoints;
        this.isActive = true;
    }

    public void update(String polygonPoints, Boolean isActive) {
        if (polygonPoints != null) this.polygonPoints = polygonPoints;
        if (isActive != null) this.isActive = isActive;
    }

}
