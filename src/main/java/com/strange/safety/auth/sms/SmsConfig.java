package com.strange.safety.auth.sms;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(SmsProperties.class)
public class SmsConfig {

    @Bean
    public SmsSender smsSender(SmsProperties smsProperties, ObjectProvider<RestClient.Builder> builderProvider,
                               Environment environment) {
        if (!smsProperties.isEnabled() || "mock".equalsIgnoreCase(smsProperties.getProvider())) {
            validateMockProfile(environment);
            return new MockSmsSender();
        }
        if ("cool-sms".equalsIgnoreCase(smsProperties.getProvider())) {
            validateCoolSmsProperties(smsProperties);
            RestClient restClient = builderProvider.getIfAvailable(RestClient::builder)
                    .requestFactory(ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                            .withConnectTimeout(timeout(smsProperties))
                            .withReadTimeout(timeout(smsProperties))))
                    .build();
            return new CoolSmsSender(smsProperties, restClient);
        }
        throw new CustomException(ErrorCode.SMS_PROVIDER_CONFIG_INVALID);
    }

    private void validateMockProfile(Environment environment) {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "dev".equalsIgnoreCase(profile)) {
                throw new CustomException(ErrorCode.SMS_PROVIDER_CONFIG_INVALID);
            }
        }
    }

    private void validateCoolSmsProperties(SmsProperties smsProperties) {
        SmsProperties.CoolSms coolSms = smsProperties.getCoolSms();
        if (isBlank(coolSms.getApiKey()) || isBlank(coolSms.getApiSecret()) || isBlank(coolSms.getFromNumber())) {
            throw new CustomException(ErrorCode.SMS_PROVIDER_CONFIG_INVALID);
        }
    }

    private Duration timeout(SmsProperties smsProperties) {
        Duration timeout = smsProperties.getCoolSms().getTimeout();
        return timeout == null ? Duration.ofMillis(3000) : timeout;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
