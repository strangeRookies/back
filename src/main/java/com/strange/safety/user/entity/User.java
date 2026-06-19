package com.strange.safety.user.entity;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "phone_verified", nullable = false)
    private boolean phoneVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status;

    @Builder
    private User(String email, String passwordHash, String name, String phoneNumber,
                 boolean phoneVerified, Role role, UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.phoneVerified = phoneVerified;
        this.role = role;
        this.status = status == null ? UserStatus.ACTIVE : status;
    }

    public static User create(String email, String passwordHash, String name, String phoneNumber, Role role) {
        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .name(name)
                .phoneNumber(phoneNumber)
                .phoneVerified(true)
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    public void updateProfile(String name, String email, String phoneNumber) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAWN;
    }

    public void updateContactInfo(String name, String phoneNumber) {
        this.name = name;
        this.phoneNumber = phoneNumber;
    }

    public void updateStatus(UserStatus status) {
        this.status = status;
    }
}
