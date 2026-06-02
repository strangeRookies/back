package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.AgeGroup;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProtectedTargetRequest {

    private String targetName;
    private String relationship;
    private AgeGroup ageGroup;
}
