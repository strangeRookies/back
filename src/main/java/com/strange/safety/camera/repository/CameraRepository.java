package com.strange.safety.camera.repository;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatus;
import com.strange.safety.facility.entity.AccessType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByFacility_Id(Long facilityId);
    List<Camera> findByFacility_IdAndStatus(Long facilityId, CameraStatus status);
    List<Camera> findByAiEnabledTrueAndStatus(CameraStatus status);

    @Query("SELECT DISTINCT c FROM Camera c " +
           "LEFT JOIN FETCH c.roiConfigs roi " +
           "LEFT JOIN FETCH roi.scenario " +
           "WHERE c.aiEnabled = true AND c.status = :status")
    List<Camera> findAiEnabledWithRoisAndScenarios(@Param("status") CameraStatus status);
    Optional<Camera> findFirstByCameraLoginIdOrderByIdDesc(String cameraLoginId);
    Optional<Camera> findFirstByCameraLoginIdAndStatusOrderByIdDesc(String cameraLoginId, CameraStatus status);
    boolean existsByCameraLoginId(String cameraLoginId);
    long countByConnectionStatus(CameraConnectionStatus connectionStatus);

    @Query("SELECT uf.user.id, COUNT(c) FROM Camera c " +
            "JOIN UserFacility uf ON uf.facility = c.facility " +
            "WHERE uf.user.id IN :userIds AND uf.accessType = :accessType " +
            "GROUP BY uf.user.id")
    List<Object[]> countCamerasByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("accessType") AccessType accessType);
}
