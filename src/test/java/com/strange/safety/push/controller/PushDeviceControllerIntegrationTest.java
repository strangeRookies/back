package com.strange.safety.push.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.security.CustomUserDetails;
import com.strange.safety.event.MqttSafetyEventSubscriber;
import com.strange.safety.push.entity.PushDevice;
import com.strange.safety.push.repository.PushDeviceRepository;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.repository.UserRepository;
import com.strange.safety.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PushDeviceControllerIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired PushDeviceRepository pushDeviceRepository;
    @Autowired UserService userService;

    @MockBean MqttSafetyEventSubscriber mqttSafetyEventSubscriber;

    @Test
    void authenticatedUserRegistersAndDeactivatesCurrentDevice() throws Exception {
        User saved = saveUser("push-owner@example.com");
        CustomUserDetails principal = principal(saved);

        mockMvc.perform(post("/api/push/devices")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("token-1", "device-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.registered").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist());

        PushDevice registered = pushDeviceRepository.findByToken("token-1").orElseThrow();
        assertThat(registered.getUser().getId()).isEqualTo(saved.getId());
        assertThat(registered.isActive()).isTrue();

        mockMvc.perform(delete("/api/push/devices")
                        .with(user(principal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"token-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(pushDeviceRepository.findByToken("token-1").orElseThrow().isActive()).isFalse();
    }

    @Test
    void sameTokenIsReassignedWithoutDuplicateRow() throws Exception {
        User first = saveUser("push-first@example.com");
        User second = saveUser("push-second@example.com");

        mockMvc.perform(post("/api/push/devices")
                        .with(user(principal(first)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("shared-token", "first-device")))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/push/devices")
                        .with(user(principal(second)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("shared-token", "second-device")))
                .andExpect(status().isOk());

        assertThat(pushDeviceRepository.count()).isEqualTo(1);
        PushDevice reassigned = pushDeviceRepository.findByToken("shared-token").orElseThrow();
        assertThat(reassigned.getUser().getId()).isEqualTo(second.getId());
        assertThat(reassigned.getDeviceId()).isEqualTo("second-device");
    }

    @Test
    void unauthenticatedAndInvalidRegistrationAreRejected() throws Exception {
        mockMvc.perform(post("/api/push/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("token-1", "device-1")))
                .andExpect(status().isUnauthorized());

        User saved = saveUser("push-validation@example.com");
        mockMvc.perform(post("/api/push/devices")
                        .with(user(principal(saved)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void accountWithdrawalDeactivatesAllDevices() throws Exception {
        User saved = saveUser("push-withdraw@example.com");
        mockMvc.perform(post("/api/push/devices")
                        .with(user(principal(saved)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("withdraw-token", "device-1")))
                .andExpect(status().isOk());

        userService.deleteAccount(saved.getId());

        assertThat(pushDeviceRepository.findByToken("withdraw-token").orElseThrow().isActive()).isFalse();
    }

    @Test
    void testEndpointExistsOnlyForTestProfileAndSkipsWhenFcmDisabled() throws Exception {
        User saved = saveUser("push-test@example.com");

        mockMvc.perform(post("/api/push/test").with(user(principal(saved))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attempted").value(0))
                .andExpect(jsonPath("$.data.success").value(0))
                .andExpect(jsonPath("$.data.failure").value(0));
    }

    private User saveUser(String email) {
        return userRepository.save(User.create(
                email, "password-hash", "푸시 사용자", "01012345678", Role.INDIVIDUAL));
    }

    private CustomUserDetails principal(User user) {
        return new CustomUserDetails(user.getId(), user.getEmail(), user.getRole());
    }

    private String registerBody(String token, String deviceId) {
        return """
                {
                  "token": "%s",
                  "deviceId": "%s",
                  "platform": "ANDROID",
                  "appVersion": "1.0.0"
                }
                """.formatted(token, deviceId);
    }
}
