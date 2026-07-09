package com.strange.safety.vlm.repository;

import com.strange.safety.vlm.entity.AlertEventDescription;
import com.strange.safety.vlm.entity.VlmJobStatus;
import com.strange.safety.vlm.entity.VlmSourceType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AlertEventDescriptionRepository extends JpaRepository<AlertEventDescription, Long> {

    boolean existsBySourceAssetTypeAndSourceAssetKeyAndPromptVersionAndVlmModelName(
            VlmSourceType sourceAssetType,
            String sourceAssetKey,
            String promptVersion,
            String vlmModelName
    );

    @Query("""
            select d from AlertEventDescription d
            join fetch d.alertEvent e
            left join fetch e.camera c
            left join fetch e.corporateCamera cc
            join fetch e.scenario s
            where (d.status = com.strange.safety.vlm.entity.VlmJobStatus.PENDING
                or (d.status = com.strange.safety.vlm.entity.VlmJobStatus.PROCESSING and d.lockedUntil < :now))
              and d.retryCount < d.maxRetries
            order by d.id asc
            """)
    List<AlertEventDescription> findLockableJobs(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            select d from AlertEventDescription d
            join fetch d.alertEvent e
            join fetch e.camera c
            join fetch c.facility f
            join fetch e.scenario s
            where d.status = :status
              and d.descriptionEmbedding is not null
              and f.id = :facilityId
              and (:dateFrom is null or e.detectedAt >= :dateFrom)
              and (:dateTo is null or e.detectedAt <= :dateTo)
              and (:cameraId is null or c.id = :cameraId)
              and (:excludeMock = false or d.mockResult = false)
            """)
    List<AlertEventDescription> findSearchableForFacility(
            @Param("facilityId") Long facilityId,
            @Param("status") VlmJobStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("cameraId") Long cameraId,
            @Param("excludeMock") boolean excludeMock
    );

    @Query("""
            select d from AlertEventDescription d
            join fetch d.alertEvent e
            join fetch e.corporateCamera c
            join fetch c.companyProfile p
            join fetch e.scenario s
            where d.status = :status
              and d.descriptionEmbedding is not null
              and p.id = :companyProfileId
              and (:dateFrom is null or e.detectedAt >= :dateFrom)
              and (:dateTo is null or e.detectedAt <= :dateTo)
              and (:cameraId is null or c.id = :cameraId)
              and (:excludeMock = false or d.mockResult = false)
            """)
    List<AlertEventDescription> findSearchableForCompany(
            @Param("companyProfileId") Long companyProfileId,
            @Param("status") VlmJobStatus status,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("cameraId") Long cameraId,
            @Param("excludeMock") boolean excludeMock
    );

    Optional<AlertEventDescription> findFirstByAlertEvent_Id(Long alertEventId);
}
