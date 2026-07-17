package com.strange.safety.corporatecamera.repository;

import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.corporatecamera.entity.CorporateCamera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CorporateCameraRepository extends JpaRepository<CorporateCamera, Long> {
    List<CorporateCamera> findByCompanyProfile_Id(Long companyProfileId);
    List<CorporateCamera> findByStatus(com.strange.safety.camera.entity.CameraStatus status);
    java.util.Optional<CorporateCamera> findFirstByCameraLoginIdOrderByIdDesc(String cameraLoginId);
    java.util.Optional<CorporateCamera> findFirstByCameraLoginIdAndStatusOrderByIdDesc(String cameraLoginId, com.strange.safety.camera.entity.CameraStatus status);
    boolean existsByCameraLoginId(String cameraLoginId);
    long countByConnectionStatus(CameraConnectionStatus connectionStatus);

    @Query("SELECT c.companyProfile.id, COUNT(c) FROM CorporateCamera c WHERE c.companyProfile.id IN :ids GROUP BY c.companyProfile.id")
    List<Object[]> countCamerasByCompanyProfileIds(@Param("ids") Collection<Long> ids);

    @Query("SELECT DISTINCT c FROM CorporateCamera c " +
           "LEFT JOIN FETCH c.roiConfigs roi " +
           "LEFT JOIN FETCH roi.scenario " +
           "WHERE c.status = :status")
    List<CorporateCamera> findWithRoisAndScenarios(@Param("status") com.strange.safety.camera.entity.CameraStatus status);
}
