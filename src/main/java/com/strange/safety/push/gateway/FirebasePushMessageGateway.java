package com.strange.safety.push.gateway;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.strange.safety.push.config.FcmProperties;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.fcm", name = "enabled", havingValue = "true")
public class FirebasePushMessageGateway implements PushMessageGateway {

    private final FirebaseMessaging firebaseMessaging;
    private final FcmProperties properties;

    @Override
    public List<PushSendResult> send(List<String> tokens, PushMessagePayload payload) throws Exception {
        if (tokens.isEmpty()) {
            return List.of();
        }
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(payload.title())
                        .setBody(payload.body())
                        .build())
                .putAllData(payload.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setTtl(properties.getTtlMs())
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(properties.getChannelId())
                                .setSound("default")
                                .build())
                        .build())
                .build();

        BatchResponse response = firebaseMessaging.sendEachForMulticast(message);
        List<PushSendResult> results = new ArrayList<>(response.getResponses().size());
        for (SendResponse sendResponse : response.getResponses()) {
            if (sendResponse.isSuccessful()) {
                results.add(PushSendResult.success(sendResponse.getMessageId()));
            } else {
                String errorCode = sendResponse.getException().getMessagingErrorCode() != null
                        ? sendResponse.getException().getMessagingErrorCode().name()
                        : String.valueOf(sendResponse.getException().getErrorCode());
                results.add(PushSendResult.failure(errorCode));
            }
        }
        return results;
    }
}
