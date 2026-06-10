package com.strange.safety.user.dto;

import com.strange.safety.user.entity.User;

public record AdminUserResponse(
        Long userId,
        String role,
        String name,
        String representative,
        String contact,
        String email,
        String region,
        String registeredAt,
        String status,
        int cameraCount
) {

    public static AdminUserResponse from(User user, String name, String representative,
                                         String contact, String region, int cameraCount) {
        return new AdminUserResponse(
                user.getId(),
                user.getRole().name(),
                name,
                representative,
                contact,
                user.getEmail(),
                region,
                user.getCreatedAt() != null ? user.getCreatedAt().toLocalDate().toString() : "",
                user.getStatus().name(),
                cameraCount
        );
    }
}
