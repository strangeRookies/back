package com.strange.safety.company.entity;

import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_profile_id")
    private Long id;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Builder
    private CompanyProfile(String companyName) {
        this.companyName = companyName;
    }
}
