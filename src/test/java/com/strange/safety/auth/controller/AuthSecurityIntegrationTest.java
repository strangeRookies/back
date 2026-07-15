package com.strange.safety.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.entity.SmsVerification;
import com.strange.safety.auth.entity.VerificationPurpose;
import com.strange.safety.auth.repository.SmsVerificationRepository;
import com.strange.safety.auth.security.RefreshTokenHasher;
import com.strange.safety.auth.security.JwtTokenProvider;
import com.strange.safety.auth.security.LoginAttemptStore;
import com.strange.safety.auth.sms.SmsVerificationStore;
import com.strange.safety.auth.session.RefreshTokenStore;
import com.strange.safety.event.MqttSafetyEventSubscriber;
import com.strange.safety.user.entity.User;
import com.strange.safety.user.entity.AgreementType;
import com.strange.safety.user.entity.UserAgreement;
import com.strange.safety.user.repository.UserAgreementRepository;
import com.strange.safety.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserRepository userRepository;
    @Autowired UserAgreementRepository userAgreementRepository;
    @Autowired RefreshTokenStore refreshTokenStore;
    @Autowired SmsVerificationRepository smsVerificationRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtTokenProvider jwtTokenProvider;
    @Autowired RefreshTokenHasher tokenHasher;
    @Autowired LoginAttemptStore loginAttemptStore;
    @Autowired SmsVerificationStore smsVerificationStore;

    @MockBean MqttSafetyEventSubscriber mqttSafetyEventSubscriber;

    private User activeUser;

    @BeforeEach
    void setUp() {
        smsVerificationRepository.deleteAll();
        userAgreementRepository.deleteAll();
        userRepository.deleteAll();
        activeUser = userRepository.save(User.create(
                "security@example.com",
                passwordEncoder.encode("Password123!"),
                "보안 테스트 사용자",
                "01012345678",
                Role.INDIVIDUAL
        ));
        loginAttemptStore.clear("security@example.com");
        smsVerificationStore.clear("01012345678");
        refreshTokenStore.revokeAllByUserId(activeUser.getId());
        userAgreementRepository.save(UserAgreement.create(
                activeUser, AgreementType.TERMS, true, true, LocalDateTime.now()));
        userAgreementRepository.save(UserAgreement.create(
                activeUser, AgreementType.PRIVACY, true, true, LocalDateTime.now()));
        userAgreementRepository.save(UserAgreement.create(
                activeUser, AgreementType.MARKETING, false, true, LocalDateTime.now()));
    }

    @Test
    void usersMeRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void usersMeReturnsUserWithValidJwt() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(activeUser);

        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("security@example.com"))
                .andExpect(jsonPath("$.data.role").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void usersMeAgreementsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/users/me/agreements"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void usersMeAgreementsReturnsAgreementsWithValidJwt() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(activeUser);

        mockMvc.perform(get("/api/users/me/agreements")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void forbiddenEndpointReturnsAccessDeniedCode() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(activeUser);

        mockMvc.perform(get("/api/inquiries")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_ACCESS_DENIED"));
    }

    @Test
    void marketingAgreementCanBeWithdrawnWithValidJwt() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(activeUser);

        mockMvc.perform(patch("/api/users/me/agreements/marketing")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "agreed": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.agreementType").value("MARKETING"))
                .andExpect(jsonPath("$.data.agreed").value(false))
                .andExpect(jsonPath("$.data.withdrawnAt").exists());
    }

    @Test
    void loginRejectsMismatchedAccountType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "password": "Password123!",
                                  "accountType": "CORPORATE"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void loginLocksAfterFiveFailures() throws Exception {
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "security@example.com",
                                      "password": "WrongPassword123!",
                                      "accountType": "INDIVIDUAL"
                                    }
                                    """))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "password": "WrongPassword123!",
                                  "accountType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_LOGIN_LOCKED"));
    }

    @Test
    void validationErrorContainsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists())
                .andExpect(jsonPath("$.error.fieldErrors.password").exists())
                .andExpect(jsonPath("$.error.fieldErrors.accountType").exists());
    }

    @Test
    void malformedJsonReturnsCommonInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "password": "Password123!",
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void emailAvailabilityPostEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/auth/email-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "new-user@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void emailAvailabilityValidationFailureReturnsCommonInvalidInput() throws Exception {
        mockMvc.perform(post("/api/auth/email-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists());
    }

    @Test
    void businessNumberAvailabilityPostEndpointIsPublic() throws Exception {
        mockMvc.perform(post("/api/companies/business-number-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessNumber": "1234567890"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void businessNumberAvailabilityValidationFailureReturnsCommonInvalidInput() throws Exception {
        mockMvc.perform(post("/api/companies/business-number-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "businessNumber": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.businessNumber").exists());
    }

    @Test
    void refreshRotationAndLogoutPreventTokenReuse() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();
        Cookie firstRefreshCookie = loginResult.getResponse().getCookie("REFRESH_TOKEN");

        MvcResult reissueResult = mockMvc.perform(post("/api/auth/reissue")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andReturn();
        JsonNode reissue = responseBody(reissueResult);
        String accessToken = reissue.path("data").path("accessToken").asText();
        Cookie secondRefreshCookie = reissueResult.getResponse().getCookie("REFRESH_TOKEN");

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(firstRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

        MvcResult logoutResult = mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .cookie(secondRefreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();
        assertThat(logoutResult.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(secondRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void reissueWithoutRefreshCookieReturnsInvalidToken() throws Exception {
        mockMvc.perform(post("/api/auth/reissue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void passwordResetSmsEndpointIsPublicAndIssuesResetVerification() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/verifications/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "phone": "010-1234-5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.verificationId").doesNotExist());

        SmsVerification verification = smsVerificationRepository.findAll().get(0);
        org.assertj.core.api.Assertions.assertThat(verification.getPhoneNumber()).isEqualTo("01012345678");
        org.assertj.core.api.Assertions.assertThat(verification.getPurpose()).isEqualTo(VerificationPurpose.RESET_PASSWORD);
    }

    @Test
    void passwordResetSmsEndpointReturnsSameShapeWhenUserDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/verifications/sms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "phone": "010-1234-5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.verificationId").doesNotExist());

        org.assertj.core.api.Assertions.assertThat(smsVerificationRepository.findAll()).isEmpty();
    }

    @Test
    void passwordResetSmsConfirmIssuesVerificationToken() throws Exception {
        SmsVerification verification = SmsVerification.issue(
                "01012345678",
                VerificationPurpose.RESET_PASSWORD,
                passwordEncoder.encode("123456"),
                Instant.now().plusSeconds(300)
        );
        smsVerificationRepository.save(verification);

        mockMvc.perform(post("/api/auth/password-reset/verifications/sms/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "phone": "010-1234-5678",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verificationToken").exists());
    }

    @Test
    void passwordResetSmsConfirmUsesGenericErrorWhenUserDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/verifications/sms/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "phone": "010-1234-5678",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_VERIFICATION"));
    }

    @Test
    void passwordResetChangesPasswordAndRevokesRefreshTokens() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isOk())
                .andReturn();
        Cookie previousRefreshCookie = loginResult.getResponse().getCookie("REFRESH_TOKEN");
        String verificationToken = verifiedToken("01012345678", VerificationPurpose.RESET_PASSWORD);

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "security@example.com",
                                "phone", "010-1234-5678",
                                "verificationToken", verificationToken,
                                "newPassword", "NewPassword123!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "security@example.com",
                                  "password": "NewPassword123!",
                                  "accountType": "INDIVIDUAL"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(previousRefreshCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void passwordResetRejectsSignupVerificationToken() throws Exception {
        String verificationToken = verifiedToken("01012345678", VerificationPurpose.SIGN_UP);

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "security@example.com",
                                "phone", "01012345678",
                                "verificationToken", verificationToken,
                                "newPassword", "NewPassword123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_VERIFICATION"));
    }

    @Test
    void passwordResetUsesGenericErrorWhenUserDoesNotExist() throws Exception {
        String verificationToken = verifiedToken("01012345678", VerificationPurpose.RESET_PASSWORD);

        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", "missing@example.com",
                                "phone", "01012345678",
                                "verificationToken", verificationToken,
                                "newPassword", "NewPassword123!"
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_VERIFICATION"));
    }

    @Test
    void passwordResetValidationFailureReturnsCommonError() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "phone": "",
                                  "verificationToken": "",
                                  "newPassword": "short"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists())
                .andExpect(jsonPath("$.error.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.error.fieldErrors.verificationToken").exists())
                .andExpect(jsonPath("$.error.fieldErrors.newPassword").exists());
    }

    private String loginRequest() {
        return """
                {
                  "email": "security@example.com",
                  "password": "Password123!",
                  "accountType": "INDIVIDUAL"
                }
                """;
    }

    private JsonNode responseBody(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String verifiedToken(String phone, VerificationPurpose purpose) {
        String token = "verified-" + purpose + "-" + System.nanoTime();
        SmsVerification verification = SmsVerification.issue(
                phone, purpose, passwordEncoder.encode("123456"), Instant.now().plusSeconds(300));
        verification.markVerified(Instant.now());
        smsVerificationRepository.save(verification);
        smsVerificationStore.saveVerifiedToken(
                tokenHasher.hash(token), phone, purpose, java.time.Duration.ofSeconds(900));
        return token;
    }
}
