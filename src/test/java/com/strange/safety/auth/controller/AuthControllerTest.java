package com.strange.safety.auth.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.strange.safety.auth.dto.AvailabilityResponse;
import com.strange.safety.auth.dto.CorporateSignupRequest;
import com.strange.safety.auth.dto.LoginRequest;
import com.strange.safety.auth.dto.TokenIssueResult;
import com.strange.safety.auth.dto.TokenResponse;
import com.strange.safety.auth.entity.Role;
import com.strange.safety.auth.security.RefreshTokenCookieManager;
import com.strange.safety.auth.service.AuthService;
import com.strange.safety.auth.service.PasswordResetService;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.auth.service.SmsVerificationService;
import com.strange.safety.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AuthService authService;
    private SignupService signupService;
    private RefreshTokenCookieManager refreshTokenCookieManager;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = org.mockito.Mockito.mock(AuthService.class);
        signupService = org.mockito.Mockito.mock(SignupService.class);
        SmsVerificationService smsVerificationService = org.mockito.Mockito.mock(SmsVerificationService.class);
        PasswordResetService passwordResetService = org.mockito.Mockito.mock(PasswordResetService.class);
        refreshTokenCookieManager = org.mockito.Mockito.mock(RefreshTokenCookieManager.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(
                        authService, signupService, smsVerificationService, passwordResetService,
                        refreshTokenCookieManager))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginReturnsCommonSuccessResponse() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new TokenIssueResult(new TokenResponse("Bearer", "access", 1800, null), "refresh"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("test@example.com", "password123", Role.INDIVIDUAL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("access"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data.expiresIn").value(1800));

        verify(refreshTokenCookieManager).add(any(), org.mockito.ArgumentMatchers.eq("refresh"));
    }

    @Test
    void loginValidationFailureReturnsCommonErrorResponse() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"bad-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"));
    }

    @Test
    void corporateSignupAcceptsRequestWithoutInstallation() throws Exception {
        mockMvc.perform(post("/api/auth/signup/corporate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "corporate@example.com",
                                  "password": "Password123!",
                                  "phone": "01055556666",
                                  "verificationToken": "verified-token",
                                  "company": {
                                    "name": "Smart Safety Hospital",
                                    "businessNumber": "123-45-67890",
                                    "industry": "medical",
                                    "size": "50-200",
                                    "postcode": "06123",
                                    "address": "Seoul Gangnam",
                                    "addressDetail": "Safety Office",
                                    "region3DepthName": "Yeoksam"
                                  },
                                  "manager": {
                                    "name": "company manager",
                                    "department": "safety team",
                                    "email": "manager@example.com",
                                    "contact": "02-555-1234"
                                  },
                                  "agreements": {
                                    "termsAgreed": true,
                                    "privacyAgreed": true,
                                    "marketingAgreed": false
                                  }
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));

        verify(signupService).signupCorporate(any(CorporateSignupRequest.class));
    }

    @Test
    void emailAvailabilityAcceptsPostBody() throws Exception {
        when(signupService.emailAvailability("test@example.com"))
                .thenReturn(new AvailabilityResponse(true));

        mockMvc.perform(post("/api/auth/email-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "test@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.available").value(true));
    }

    @Test
    void emailAvailabilityValidationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/email-availability")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.fieldErrors.email").exists());
    }

    @Test
    void emailAvailabilityDoesNotSupportGet() throws Exception {
        mockMvc.perform(get("/api/auth/email-availability")
                        .param("email", "test@example.com"))
                .andExpect(status().isMethodNotAllowed());
    }
}
