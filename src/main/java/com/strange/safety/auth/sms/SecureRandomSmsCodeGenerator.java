package com.strange.safety.auth.sms;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class SecureRandomSmsCodeGenerator implements SmsCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
