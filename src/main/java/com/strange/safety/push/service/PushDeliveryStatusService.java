package com.strange.safety.push.service;

import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.push.repository.PushNotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeliveryStatusService {

    private final PushNotificationDeliveryRepository deliveryRepository;
    private final PushDeviceRepository pushDeviceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long deliveryId, String messageId) {
        deliveryRepository.findById(deliveryId)
                .ifPresent(delivery -> delivery.markSent(messageId));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long deliveryId, Long deviceId, String errorCode, boolean permanent) {
        deliveryRepository.findById(deliveryId)
                .ifPresent(delivery -> delivery.markFailed(errorCode, permanent));
        if (permanent) {
            pushDeviceRepository.findById(deviceId).ifPresent(device -> device.deactivate());
        }
    }
}
