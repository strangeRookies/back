package com.strange.safety.push.gateway;

import java.util.Map;

public record PushMessagePayload(
        String title,
        String body,
        Map<String, String> data
) {
}
