package com.strange.safety.camera.repository;

import com.strange.safety.camera.entity.CameraConnectionStatus;
import com.strange.safety.camera.entity.CameraStatusLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface CameraStatusLogRepository extends JpaRepository<CameraStatusLog, Long> {

    /** 특정 카메라의 상태 이력을 최신순으로 조회 */
    Page<CameraStatusLog> findByCamera_IdOrderByDetectedAtDesc(Long cameraId, Pageable pageable);

    /** 특정 카메라의 최근 N개 상태 이력 조회 */
    List<CameraStatusLog> findTop10ByCamera_IdOrderByDetectedAtDesc(Long cameraId);

    /** 특정 상태로 전환된 이력 수 (모니터링/통계용) */
    long countByCamera_IdAndCurrentStatus(Long cameraId, CameraConnectionStatus status);

    /** 특정 시간 이후 특정 시설의 카메라 상태 이력 조회 */
    List<CameraStatusLog> findByCamera_Facility_IdAndDetectedAtAfterOrderByDetectedAtDesc(
            Long facilityId, Instant after);
}
