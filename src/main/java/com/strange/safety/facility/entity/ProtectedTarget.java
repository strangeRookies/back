package com.strange.safety.facility.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.user.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "protected_targets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProtectedTarget extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "protected_target_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id", nullable = false)
    private User guardianUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facility_id", nullable = false)
    private Facility facility;

    @Column(name = "target_name", nullable = false)
    private String targetName;

    @Column(nullable = false)
    private String relationship;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_group", nullable = false)
    private AgeGroup ageGroup;

    @Builder
    private ProtectedTarget(User guardianUser, Facility facility, String targetName,
                            String relationship, AgeGroup ageGroup) {
        this.guardianUser = guardianUser;
        this.facility = facility;
        this.targetName = targetName;
        this.relationship = relationship;
        this.ageGroup = ageGroup;
    }

    public void update(String targetName, String relationship, AgeGroup ageGroup) {
        if (targetName != null) this.targetName = targetName;
        if (relationship != null) this.relationship = relationship;
        if (ageGroup != null) this.ageGroup = ageGroup;
    }
}
