package com.strange.safety.facility.repository;

import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.Facility;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FacilityRepository extends JpaRepository<Facility, Long> {

    @Query("SELECT f FROM Facility f JOIN UserFacility uf ON uf.facility = f " +
            "WHERE uf.user.id = :userId AND uf.accessType = :accessType AND f.isActive = true")
    Page<Facility> findActiveFacilitiesByManagerId(
            @Param("userId") Long userId,
            @Param("accessType") AccessType accessType,
            Pageable pageable);

    @Query("SELECT uf.user.id, f.district FROM Facility f " +
            "JOIN UserFacility uf ON uf.facility = f " +
            "WHERE uf.user.id IN :userIds AND uf.accessType = :accessType AND f.isActive = true")
    List<Object[]> findDistrictsByUserIds(
            @Param("userIds") List<Long> userIds,
            @Param("accessType") AccessType accessType);
}
