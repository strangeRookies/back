package com.strange.safety.push.entity;

import com.strange.safety.common.entity.BaseEntity;
import com.strange.safety.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "push_devices", uniqueConstraints = {
        @UniqueConstraint(name = "uk_push_devices_token", columnNames = "token")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDevice extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_device_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(name = "device_id", length = 255)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PushPlatform platform;

    @Column(name = "app_version", length = 50)
    private String appVersion;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    private PushDevice(User user, String token, String deviceId, PushPlatform platform, String appVersion) {
        register(user, deviceId, platform, appVersion);
        this.token = token;
    }

    public static PushDevice create(User user, String token, String deviceId,
                                    PushPlatform platform, String appVersion) {
        return new PushDevice(user, token, deviceId, platform, appVersion);
    }

    public void register(User user, String deviceId, PushPlatform platform, String appVersion) {
        this.user = user;
        this.deviceId = deviceId;
        this.platform = platform;
        this.appVersion = appVersion;
        this.active = true;
        this.lastSeenAt = LocalDateTime.now();
    }

    public void deactivate() {
        this.active = false;
    }
}
