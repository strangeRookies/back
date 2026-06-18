package com.strange.safety.alert.repository;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.alert.entity.AlertSeverity;
import com.strange.safety.alert.entity.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertEventRepository extends JpaRepository<AlertEvent, Long>,
        JpaSpecificationExecutor<AlertEvent> {

    @Query("SELECT COUNT(a) FROM AlertEvent a JOIN a.camera c JOIN c.facility f " +
           "WHERE f.id = :facilityId AND a.detectedAt >= :dateFrom AND a.detectedAt <= :dateTo")
    long countByFacilityAndDateRange(@Param("facilityId") Long facilityId,
                                     @Param("dateFrom") LocalDateTime dateFrom,
                                     @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COUNT(a) FROM AlertEvent a JOIN a.camera c JOIN c.facility f " +
           "WHERE f.id = :facilityId AND a.severity = :severity AND a.detectedAt >= :dateFrom AND a.detectedAt <= :dateTo")
    long countByFacilityAndSeverity(@Param("facilityId") Long facilityId,
                                    @Param("severity") AlertSeverity severity,
                                    @Param("dateFrom") LocalDateTime dateFrom,
                                    @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COUNT(a) FROM AlertEvent a JOIN a.camera c JOIN c.facility f " +
           "WHERE f.id = :facilityId AND a.status = :status AND a.detectedAt >= :dateFrom AND a.detectedAt <= :dateTo")
    long countByFacilityAndStatus(@Param("facilityId") Long facilityId,
                                  @Param("status") AlertStatus status,
                                  @Param("dateFrom") LocalDateTime dateFrom,
                                  @Param("dateTo") LocalDateTime dateTo);

    @Query("SELECT COUNT(a) FROM AlertEvent a WHERE a.detectedAt >= :dateFrom")
    long countAllSince(@Param("dateFrom") LocalDateTime dateFrom);

    @Query(value = "SELECT s.scenario_type, COUNT(ae.alert_event_id) " +
                   "FROM alert_events ae " +
                   "JOIN cameras c ON ae.camera_id = c.camera_id " +
                   "JOIN scenarios s ON ae.scenario_id = s.scenario_id " +
                   "WHERE c.facility_id = :facilityId " +
                   "AND ae.detected_at >= :dateFrom AND ae.detected_at <= :dateTo " +
                   "GROUP BY s.scenario_type",
           nativeQuery = true)
    List<Object[]> countGroupByScenario(@Param("facilityId") Long facilityId,
                                        @Param("dateFrom") LocalDateTime dateFrom,
                                        @Param("dateTo") LocalDateTime dateTo);
}
