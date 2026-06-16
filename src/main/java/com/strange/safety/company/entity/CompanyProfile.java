package com.strange.safety.company.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "company_profiles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_company_profiles_business_number", columnNames = "business_registration_number")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyProfile extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_profile_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "business_registration_number", length = 20)
    private String businessRegistrationNumber;

    private String industry;
    @Column(name = "company_size")
    private String companySize;
    @Column(name = "postal_code")
    private String postalCode;
    private String address;
    @Column(name = "address_detail")
    private String addressDetail;
    private String district;
    @Column(name = "emergency_119_jurisdiction")
    private String emergency119Jurisdiction;
    @Column(name = "manager_name")
    private String managerName;
    @Column(name = "manager_department")
    private String managerDepartment;
    @Column(name = "manager_email")
    private String managerEmail;
    @Column(name = "manager_contact")
    private String managerContact;

    @Builder
    private CompanyProfile(User user, String companyName, String businessRegistrationNumber,
                           String industry, String companySize, String postalCode, String address,
                           String addressDetail, String district, String emergency119Jurisdiction,
                           String managerName, String managerDepartment,
                           String managerEmail, String managerContact) {
        this.user = user;
        this.companyName = companyName;
        this.businessRegistrationNumber = businessRegistrationNumber;
        this.industry = industry;
        this.companySize = companySize;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.district = district;
        this.emergency119Jurisdiction = emergency119Jurisdiction;
        this.managerName = managerName;
        this.managerDepartment = managerDepartment;
        this.managerEmail = managerEmail;
        this.managerContact = managerContact;
    }
}
