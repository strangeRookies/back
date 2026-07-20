package com.strange.safety.push.service;

import com.strange.safety.push.config.FcmProperties;
import com.strange.safety.push.dto.PushTestResponse;
import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.gateway.PushMessageGateway;
import com.strange.safety.push.gateway.PushMessagePayload;
import com.strange.safety.push.gateway.PushSendResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    private static final int FCM_BATCH_SIZE = 500;

    private final FcmProperties properties;
    private final ObjectProvider<PushMessageGateway> gatewayProvider;
    private final PushRecipientService recipientService;
    private final PushDeliveryReservationService reservationService;
    private final PushDeliveryStatusService statusService;
    private final PushDeviceService pushDeviceService;
    private final PushPayloadFactory payloadFactory;

    @Async("eventProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAlertCreated(AlertPushRequestedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        PushMessageGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            log.error("FCM is enabled but no PushMessageGateway is available");
            return;
        }

        List<ReservedPushDelivery> reserved = new ArrayList<>();
        for (PushRecipient recipient : recipientService.findRecipients(event)) {
            try {
                reservationService.reserve(event.alertEventId(), recipient.deviceId())
                        .ifPresent(deliveryId -> reserved.add(new ReservedPushDelivery(
                                deliveryId, recipient.deviceId(), recipient.token())));
            } catch (DataIntegrityViolationException duplicate) {
                log.debug("Skipped duplicate push delivery: alertEventId={}, deviceId={}",
                        event.alertEventId(), recipient.deviceId());
            }
        }

        PushMessagePayload payload = payloadFactory.alert(event);
        for (int from = 0; from < reserved.size(); from += FCM_BATCH_SIZE) {
            int to = Math.min(from + FCM_BATCH_SIZE, reserved.size());
            sendReservedBatch(gateway, reserved.subList(from, to), payload);
        }
    }

    public PushTestResponse sendTest(Long userId) {
        if (!properties.isEnabled()) {
            return new PushTestResponse(0, 0, 0);
        }
        PushMessageGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return new PushTestResponse(0, 0, 0);
        }

        List<PushRecipient> recipients = pushDeviceService.findActiveByUserId(userId).stream()
                .map(device -> new PushRecipient(device.getId(), device.getToken()))
                .toList();
        int success = 0;
        int failure = 0;
        for (int from = 0; from < recipients.size(); from += FCM_BATCH_SIZE) {
            int to = Math.min(from + FCM_BATCH_SIZE, recipients.size());
            List<PushRecipient> batch = recipients.subList(from, to);
            try {
                List<PushSendResult> results = gateway.send(
                        batch.stream().map(PushRecipient::token).toList(), payloadFactory.test());
                for (int i = 0; i < batch.size(); i++) {
                    PushSendResult result = resultAt(results, i);
                    if (result.success()) {
                        success++;
                    } else {
                        failure++;
                        if (isPermanent(result.errorCode())) {
                            pushDeviceService.deactivateById(batch.get(i).deviceId());
                        }
                    }
                }
            } catch (Exception ex) {
                failure += batch.size();
                log.warn("FCM test batch failed: userId={}, deviceCount={}, error={}",
                        userId, batch.size(), ex.getMessage());
            }
        }
        return new PushTestResponse(recipients.size(), success, failure);
    }

    private void sendReservedBatch(PushMessageGateway gateway, List<ReservedPushDelivery> batch,
                                   PushMessagePayload payload) {
        try {
            List<PushSendResult> results = gateway.send(
                    batch.stream().map(ReservedPushDelivery::token).toList(), payload);
            for (int i = 0; i < batch.size(); i++) {
                ReservedPushDelivery delivery = batch.get(i);
                PushSendResult result = resultAt(results, i);
                if (result.success()) {
                    statusService.markSent(delivery.deliveryId(), result.messageId());
                } else {
                    statusService.markFailed(delivery.deliveryId(), delivery.deviceId(),
                            safeErrorCode(result.errorCode()), isPermanent(result.errorCode()));
                }
            }
        } catch (Exception ex) {
            String errorCode = ex.getClass().getSimpleName();
            for (ReservedPushDelivery delivery : batch) {
                statusService.markFailed(delivery.deliveryId(), delivery.deviceId(), errorCode, false);
            }
            log.warn("FCM alert batch failed: deviceCount={}, error={}", batch.size(), ex.getMessage());
        }
    }

    private PushSendResult resultAt(List<PushSendResult> results, int index) {
        return index < results.size()
                ? results.get(index)
                : PushSendResult.failure("MISSING_FCM_RESULT");
    }

    private boolean isPermanent(String errorCode) {
        String normalized = safeErrorCode(errorCode).toUpperCase(Locale.ROOT);
        return normalized.contains("UNREGISTERED")
                || normalized.contains("INVALID_ARGUMENT")
                || normalized.contains("SENDER_ID_MISMATCH");
    }

    private String safeErrorCode(String errorCode) {
        return errorCode == null || errorCode.isBlank() ? "UNKNOWN" : errorCode;
    }
}
