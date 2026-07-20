package com.strange.safety.push.entity;

import com.strange.safety.alert.entity.AlertEvent;
import com.strange.safety.common.entity.BaseEntity;
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
@Table(name = "push_notification_deliveries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_push_delivery_event_device",
                columnNames = {"alert_event_id", "push_device_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushNotificationDelivery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "push_notification_delivery_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alert_event_id", nullable = false)
    private AlertEvent alertEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "push_device_id", nullable = false)
    private PushDevice pushDevice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PushDeliveryStatus status;

    @Column(name = "firebase_message_id", length = 255)
    private String firebaseMessageId;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    private PushNotificationDelivery(AlertEvent alertEvent, PushDevice pushDevice) {
        this.alertEvent = alertEvent;
        this.pushDevice = pushDevice;
        this.status = PushDeliveryStatus.PENDING;
    }

    public static PushNotificationDelivery pending(AlertEvent alertEvent, PushDevice pushDevice) {
        return new PushNotificationDelivery(alertEvent, pushDevice);
    }

    public void markSent(String firebaseMessageId) {
        this.status = PushDeliveryStatus.SENT;
        this.firebaseMessageId = firebaseMessageId;
        this.failureCode = null;
        this.sentAt = LocalDateTime.now();
    }

    public void markFailed(String failureCode, boolean permanent) {
        this.status = permanent ? PushDeliveryStatus.FAILED_PERMANENT : PushDeliveryStatus.FAILED_TRANSIENT;
        this.failureCode = failureCode;
        this.firebaseMessageId = null;
        this.sentAt = null;
    }
}
