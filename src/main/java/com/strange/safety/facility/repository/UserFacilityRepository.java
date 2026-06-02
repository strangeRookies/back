package com.strange.safety.facility.repository;

import com.strange.safety.facility.entity.AccessType;
import com.strange.safety.facility.entity.UserFacility;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFacilityRepository extends JpaRepository<UserFacility, Long> {

    boolean existsByUser_IdAndFacility_IdAndAccessType(Long userId, Long facilityId, AccessType accessType);
}
