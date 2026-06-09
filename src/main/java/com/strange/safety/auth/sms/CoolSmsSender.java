package com.strange.safety.auth.sms;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Slf4j
public class CoolSmsSender implements SmsSender {

    private static final String API_URL = "https://api.coolsms.co.kr/messages/v4/send";

    private final SmsProperties smsProperties;
    private final RestClient restClient;

    public CoolSmsSender(SmsProperties smsProperties, RestClient restClient) {
        this.smsProperties = smsProperties;
        this.restClient = restClient;
    }

    @Override
    public void send(String phoneNumber, String message) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("message", Map.of(
                    "to", phoneNumber,
                    "from", smsProperties.getCoolSms().getFromNumber(),
                    "text", message
            ));

            restClient.post()
                    .uri(API_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorizationHeader())
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception exception) {
            log.warn("CoolSMS send failed. phone={}", maskPhone(phoneNumber), exception);
            throw new SmsSendException("CoolSMS send failed", exception);
        }
    }

    private String authorizationHeader() throws Exception {
        String salt = UUID.randomUUID().toString().replace("-", "");
        String date = Instant.now().toString();
        String apiKey = smsProperties.getCoolSms().getApiKey();
        String signature = hmacSha256(date + salt, smsProperties.getCoolSms().getApiSecret());
        return "HMAC-SHA256 apiKey=" + apiKey + ", date=" + date + ", salt=" + salt + ", signature=" + signature;
    }

    private String hmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 7) {
            return "****";
        }
        return phoneNumber.substring(0, 3) + "****" + phoneNumber.substring(phoneNumber.length() - 4);
    }
}
