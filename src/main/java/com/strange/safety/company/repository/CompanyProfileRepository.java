package com.strange.safety.company.repository;

import com.strange.safety.company.entity.CompanyProfile;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, Long> {
    boolean existsByBusinessRegistrationNumber(String businessRegistrationNumber);

    @Query("SELECT cp FROM CompanyProfile cp WHERE cp.user.id IN :userIds")
    List<CompanyProfile> findByUserIdIn(@Param("userIds") List<Long> userIds);
}
