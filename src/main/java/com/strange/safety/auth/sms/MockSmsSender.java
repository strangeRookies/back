package com.strange.safety.auth.sms;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockSmsSender implements SmsSender {

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[MOCK-SMS] phone={}, message={}", maskPhone(phoneNumber), message);
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
