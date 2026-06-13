package com.strange.safety.auth.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class SmsConfigTest {

    private final SmsConfig smsConfig = new SmsConfig();

    @Test
    void disabledSmsIsAllowedInProdProfile() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(false);

        SmsSender sender = smsConfig.smsSender(properties, null, environment("prod"));

        assertThat(sender).isInstanceOf(MockSmsSender.class);
    }

    @Test
    void disabledSmsIsAllowedInDevProfile() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(false);

        SmsSender sender = smsConfig.smsSender(properties, null, environment("dev"));

        assertThat(sender).isInstanceOf(MockSmsSender.class);
    }

    @Test
    void enabledMockSmsIsRejectedInProdProfile() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setProvider("mock");

        assertThatThrownBy(() -> smsConfig.smsSender(properties, null, environment("prod")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SMS_PROVIDER_CONFIG_INVALID);
    }

    @Test
    void enabledCoolSmsRequiresProviderProperties() {
        SmsProperties properties = new SmsProperties();
        properties.setEnabled(true);
        properties.setProvider("cool-sms");

        assertThatThrownBy(() -> smsConfig.smsSender(properties, null, new MockEnvironment()))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SMS_PROVIDER_CONFIG_INVALID);
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
