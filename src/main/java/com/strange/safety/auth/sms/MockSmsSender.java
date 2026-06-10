package com.strange.safety.auth.sms;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MockSmsSender implements SmsSender {

    private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("\\b\\d{6}\\b");

    @Override
    public void send(String phoneNumber, String message) {
        log.info("[MOCK-SMS] phone={}, verificationCode={}", maskPhone(phoneNumber), verificationCode(message));
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }

    private String verificationCode(String message) {
        if (message == null) {
            return "unknown";
        }
        Matcher matcher = VERIFICATION_CODE_PATTERN.matcher(message);
        return matcher.find() ? matcher.group() : "unknown";
    }
}
