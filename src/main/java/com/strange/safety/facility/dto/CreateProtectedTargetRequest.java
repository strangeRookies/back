package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.AgeGroup;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateProtectedTargetRequest {

    @NotBlank(message = "보호 대상자 이름은 필수입니다.")
    private String targetName;

    @NotBlank(message = "관계는 필수입니다.")
    private String relationship;

    @NotNull(message = "연령대는 필수입니다.")
    private AgeGroup ageGroup;
}
