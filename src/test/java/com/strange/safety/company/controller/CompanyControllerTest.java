package com.strange.safety.company.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.strange.safety.auth.dto.AvailabilityResponse;
import com.strange.safety.auth.service.SignupService;
import com.strange.safety.common.exception.GlobalExceptionHandler;
import com.strange.safety.company.repository.CompanyProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompanyControllerTest {

    private SignupService signupService;
    private CompanyProfileRepository companyProfileRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        signupService = org.mockito.Mockito.mock(SignupService.class);
        companyProfileRepository = org.mockito.Mockito.mock(CompanyProfileRepository.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CompanyController(signupService, companyProfileRepository))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void businessNumberAvailabilityAcceptsPostBody() throws Exception {
        when(signupService.businessNumberAvailability(anyString()))
                .thenReturn(new AvailabilityResponse(true));

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
    void businessNumberAvailabilityValidationFailureReturnsFieldErrors() throws Exception {
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
    void businessNumberAvailabilityDoesNotSupportGet() throws Exception {
        mockMvc.perform(get("/api/companies/business-number-availability")
                        .param("businessNumber", "1234567890"))
                .andExpect(status().isMethodNotAllowed());
    }
}
