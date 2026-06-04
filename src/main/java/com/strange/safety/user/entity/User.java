package com.strange.safety.user.entity;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
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

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder
    private User(String email, String passwordHash, String name, String phoneNumber, Role role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.phoneVerified = false;
        this.role = role == null ? Role.USER : role;
        this.isActive = true;
    }

    public static User create(String email, String passwordHash, String name, String phoneNumber) {
        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .name(name)
                .phoneNumber(phoneNumber)
                .role(Role.USER)
                .build();
    }
}
