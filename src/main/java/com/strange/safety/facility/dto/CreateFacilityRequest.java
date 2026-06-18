package com.strange.safety.facility.dto;

import com.strange.safety.facility.entity.FacilityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateFacilityRequest {

    @NotBlank(message = "시설명은 필수입니다.")
    private String facilityName;

    @NotNull(message = "시설 유형은 필수입니다.")
    private FacilityType facilityType;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    private String addressDetail;
    private String postalCode;
    private String emergency119Jurisdiction;
}
