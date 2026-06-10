package com.strange.safety.auth.sms;

public class SmsSendException extends RuntimeException {

    public SmsSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
