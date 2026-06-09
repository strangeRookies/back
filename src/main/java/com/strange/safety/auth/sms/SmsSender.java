package com.strange.safety.auth.sms;

public interface SmsSender {

    void send(String phoneNumber, String message);
}
