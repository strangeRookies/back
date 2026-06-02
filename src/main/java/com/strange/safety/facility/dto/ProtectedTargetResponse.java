package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.AgeGroup;
import com.strange.safety.facility.entity.ProtectedTarget;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProtectedTargetResponse {

    private Long protectedTargetId;
    private Long facilityId;
    private Long guardianUserId;
    private String targetName;
    private String relationship;
    private AgeGroup ageGroup;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static ProtectedTargetResponse from(ProtectedTarget target) {
        return ProtectedTargetResponse.builder()
                .protectedTargetId(target.getId())
                .facilityId(target.getFacility().getId())
                .guardianUserId(target.getGuardianUser().getId())
                .targetName(target.getTargetName())
                .relationship(target.getRelationship())
                .ageGroup(target.getAgeGroup())
                .createdAt(target.getCreatedAt())
                .updatedAt(target.getUpdatedAt())
                .build();
    }
}
