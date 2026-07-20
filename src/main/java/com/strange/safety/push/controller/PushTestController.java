package com.strange.safety.push.controller;

import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.common.response.ApiResponse;
import com.strange.safety.push.dto.PushTestResponse;
import com.strange.safety.push.service.PushNotificationDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push/test")
@Profile({"local", "test"})
@RequiredArgsConstructor
public class PushTestController {

    private final PushNotificationDispatcher dispatcher;

    @PostMapping
    public ApiResponse<PushTestResponse> sendTest(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(dispatcher.sendTest(userDetails.getUserId()));
    }
}
