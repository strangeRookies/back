package com.strange.safety.push.repository;

import com.strange.safety.push.entity.PushNotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushNotificationDeliveryRepository extends JpaRepository<PushNotificationDelivery, Long> {

    boolean existsByAlertEvent_IdAndPushDevice_Id(Long alertEventId, Long pushDeviceId);
}
