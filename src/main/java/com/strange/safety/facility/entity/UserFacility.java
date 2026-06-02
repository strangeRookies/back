package com.strange.safety.facility.entity;

import com.strange.safety.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_facility",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "facility_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFacility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_facility_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Enumerated(EnumType.STRING)
    @Column(name = "access_type", nullable = false)
    private AccessType accessType;

    @Builder
    private UserFacility(User user, Facility facility, AccessType accessType) {
        this.user = user;
        this.facility = facility;
        this.accessType = accessType;
    }
}
