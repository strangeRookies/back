package com.strange.safety.push.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.strange.safety.push.config.FcmProperties;
import com.strange.safety.push.event.AlertPushRequestedEvent;
import com.strange.safety.push.gateway.PushMessageGateway;
import com.strange.safety.push.gateway.PushSendResult;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatcherTest {

    @Mock ObjectProvider<PushMessageGateway> gatewayProvider;
    @Mock PushMessageGateway gateway;
    @Mock PushRecipientService recipientService;
    @Mock PushDeliveryReservationService reservationService;
    @Mock PushDeliveryStatusService statusService;
    @Mock PushDeviceService pushDeviceService;

    private FcmProperties properties;
    private PushNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        properties = new FcmProperties();
        properties.setEnabled(true);
        lenient().when(gatewayProvider.getIfAvailable()).thenReturn(gateway);
        dispatcher = new PushNotificationDispatcher(
                properties, gatewayProvider, recipientService, reservationService,
                statusService, pushDeviceService, new PushPayloadFactory());
    }

    @Test
    void splitsRecipientsIntoBatchesOfFiveHundred() throws Exception {
        List<PushRecipient> recipients = IntStream.rangeClosed(1, 501)
                .mapToObj(i -> new PushRecipient((long) i, "token-" + i))
                .toList();
        when(recipientService.findRecipients(any())).thenReturn(recipients);
        when(reservationService.reserve(any(), any())).thenAnswer(invocation ->
                Optional.of(invocation.<Long>getArgument(1) + 1000L));
        when(gateway.send(anyList(), any())).thenAnswer(invocation -> {
            List<String> tokens = invocation.getArgument(0);
            return tokens.stream().map(token -> PushSendResult.success("message-" + token)).toList();
        });

        dispatcher.onAlertCreated(event());

        verify(gateway, times(2)).send(anyList(), any());
        verify(statusService, times(501)).markSent(any(), any());
    }

    @Test
    void permanentFailureMarksDeliveryAndDeactivatesThroughStatusService() throws Exception {
        when(recipientService.findRecipients(any())).thenReturn(List.of(new PushRecipient(10L, "token")));
        when(reservationService.reserve(100L, 10L)).thenReturn(Optional.of(200L));
        when(gateway.send(anyList(), any())).thenReturn(List.of(PushSendResult.failure("UNREGISTERED")));

        dispatcher.onAlertCreated(event());

        verify(statusService).markFailed(200L, 10L, "UNREGISTERED", true);
    }

    @Test
    void disabledFcmSkipsRecipientAndDeliveryWork() {
        properties.setEnabled(false);

        dispatcher.onAlertCreated(event());

        verify(recipientService, never()).findRecipients(any());
        verify(reservationService, never()).reserve(any(), any());
    }

    private AlertPushRequestedEvent event() {
        return new AlertPushRequestedEvent(
                100L, 30L, "cam_03", "CCTV-03", "FACILITY", 10L,
                "FALL_BED", "CRITICAL", Instant.parse("2026-07-20T05:30:00Z"));
    }
}
