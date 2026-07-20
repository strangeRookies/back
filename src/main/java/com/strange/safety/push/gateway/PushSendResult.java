package com.strange.safety.push.gateway;

public record PushSendResult(
        boolean success,
        String messageId,
        String errorCode
) {

    public static PushSendResult success(String messageId) {
        return new PushSendResult(true, messageId, null);
    }

    public static PushSendResult failure(String errorCode) {
        return new PushSendResult(false, null, errorCode);
    }
}
