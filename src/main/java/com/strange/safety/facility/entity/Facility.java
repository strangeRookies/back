package com.strange.safety.facility.entity;

import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "facilities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "facility_id")
    private Long id;

    @Column(name = "facility_name", nullable = false)
    private String facilityName;

    @Enumerated(EnumType.STRING)
    @Column(name = "facility_type", nullable = false)
    private FacilityType facilityType;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(nullable = false)
    private String address;

    @Column(name = "address_detail")
    private String addressDetail;

    @Column(length = 100)
    private String district;

    @Column(name = "emergency_119_jurisdiction")
    private String emergency119Jurisdiction;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    private Facility(String facilityName, FacilityType facilityType,
                     String postalCode, String address, String addressDetail, String district,
                     String emergency119Jurisdiction) {
        this.facilityName = facilityName;
        this.facilityType = facilityType;
        this.postalCode = postalCode;
        this.address = address;
        this.addressDetail = addressDetail;
        this.district = district;
        this.emergency119Jurisdiction = emergency119Jurisdiction;
        this.isActive = true;
    }

    public void update(String facilityName, String address, String addressDetail,
                       String postalCode, String emergency119Jurisdiction) {
        if (facilityName != null) this.facilityName = facilityName;
        if (address != null) this.address = address;
        if (addressDetail != null) this.addressDetail = addressDetail;
        if (postalCode != null) this.postalCode = postalCode;
        if (emergency119Jurisdiction != null) this.emergency119Jurisdiction = emergency119Jurisdiction;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
