package com.strange.safety.user.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateProfileRequest {
    private String name;
    private String email;
    private String phoneNumber;
}
