package com.strange.safety.camera.repository;

import com.strange.safety.camera.entity.Camera;
import com.strange.safety.facility.entity.AccessType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CameraRepository extends JpaRepository<Camera, Long> {
    List<Camera> findByFacility_Id(Long facilityId);
    Optional<Camera> findByCameraLoginId(String cameraLoginId);

    @Query("SELECT uf.user.id, COUNT(c) FROM Camera c " +
            "JOIN UserFacility uf ON uf.facility = c.facility " +
            "WHERE uf.user.id IN :userIds AND uf.accessType = :accessType " +
            "GROUP BY uf.user.id")
    List<Object[]> countCamerasByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("accessType") AccessType accessType);
}
