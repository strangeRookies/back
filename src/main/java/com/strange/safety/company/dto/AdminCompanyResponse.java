package com.strange.safety.company.dto;

import com.strange.safety.company.entity.CompanyProfile;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminCompanyResponse {

    private Long companyProfileId;
    private String companyName;

    public static AdminCompanyResponse from(CompanyProfile companyProfile) {
        return AdminCompanyResponse.builder()
                .companyProfileId(companyProfile.getId())
                .companyName(companyProfile.getCompanyName())
                .build();
    }
}
