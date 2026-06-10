package com.strange.safety.auth.sms;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sms")
public class SmsProperties {

    private boolean enabled = false;
    private String provider = "mock";
    private CoolSms coolSms = new CoolSms();
    private Message message = new Message();
    private RateLimit rateLimit = new RateLimit();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public CoolSms getCoolSms() {
        return coolSms;
    }

    public void setCoolSms(CoolSms coolSms) {
        this.coolSms = coolSms;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public static class CoolSms {
        private String apiKey;
        private String apiSecret;
        private String fromNumber;
        private Duration timeout = Duration.ofMillis(3000);

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public String getFromNumber() {
            return fromNumber;
        }

        public void setFromNumber(String fromNumber) {
            this.fromNumber = fromNumber;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Message {
        private String verificationTemplate = "[Strange Safety] 인증번호는 {code}입니다.";

        public String getVerificationTemplate() {
            return verificationTemplate;
        }

        public void setVerificationTemplate(String verificationTemplate) {
            this.verificationTemplate = verificationTemplate;
        }
    }

    public static class RateLimit {
        private long resendCooldownSeconds = 60;
        private long dailyLimitPerPhone = 5;

        public long getResendCooldownSeconds() {
            return resendCooldownSeconds;
        }

        public void setResendCooldownSeconds(long resendCooldownSeconds) {
            this.resendCooldownSeconds = resendCooldownSeconds;
        }

        public long getDailyLimitPerPhone() {
            return dailyLimitPerPhone;
        }

        public void setDailyLimitPerPhone(long dailyLimitPerPhone) {
            this.dailyLimitPerPhone = dailyLimitPerPhone;
        }

    }
}
