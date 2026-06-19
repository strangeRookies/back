package com.strange.safety.user.dto;

public record AdminMemberUpdateRequest(
        String name,
        String representative,
        String contact,
        String region,
        String status
) {
}
