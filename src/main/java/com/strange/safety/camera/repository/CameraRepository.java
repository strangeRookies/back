package com.strange.safety.camera.repository;

import com.strange.safety.camera.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByFacility_Id(Long facilityId);
    Optional<Camera> findByCameraLoginId(String cameraLoginId);
}
