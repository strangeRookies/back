package com.strange.safety.push.service;

import com.strange.safety.alert.repository.AlertEventRepository;
import com.strange.safety.push.entity.PushNotificationDelivery;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.push.repository.PushNotificationDeliveryRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeliveryReservationService {

    private final PushNotificationDeliveryRepository deliveryRepository;
    private final AlertEventRepository alertEventRepository;
    private final PushDeviceRepository pushDeviceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Long> reserve(Long alertEventId, Long pushDeviceId) {
        if (deliveryRepository.existsByAlertEvent_IdAndPushDevice_Id(alertEventId, pushDeviceId)) {
            return Optional.empty();
        }
        PushNotificationDelivery delivery = PushNotificationDelivery.pending(
                alertEventRepository.getReferenceById(alertEventId),
                pushDeviceRepository.getReferenceById(pushDeviceId));
        return Optional.of(deliveryRepository.saveAndFlush(delivery).getId());
    }
}
