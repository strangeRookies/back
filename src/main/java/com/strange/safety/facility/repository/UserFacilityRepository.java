package com.strange.safety.facility.repository;

import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.UserFacility;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserFacilityRepository extends JpaRepository<UserFacility, Long> {

    boolean existsByUser_IdAndFacility_IdAndAccessType(Long userId, Long facilityId, AccessType accessType);

    @Query("SELECT DISTINCT uf.user.id FROM UserFacility uf " +
            "WHERE uf.facility.id = :facilityId " +
            "AND uf.accessType IN :accessTypes " +
            "AND uf.user.status = com.strange.safety.user.entity.UserStatus.ACTIVE")
    List<Long> findActiveUserIdsByFacilityId(
            @Param("facilityId") Long facilityId,
            @Param("accessTypes") List<AccessType> accessTypes);
}
