package com.strange.safety.camera.repository;

import com.strange.safety.camera.entity.RoiConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoiConfigRepository extends JpaRepository<RoiConfig, Long> {
    List<RoiConfig> findByCamera_Id(Long cameraId);
    List<RoiConfig> findByCorporateCamera_Id(Long corporateCameraId);
}
