package com.strange.safety.facility.repository;

import com.strange.safety.facility.entity.ProtectedTarget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProtectedTargetRepository extends JpaRepository<ProtectedTarget, Long> {
    Page<ProtectedTarget> findByFacility_Id(Long facilityId, Pageable pageable);
}
