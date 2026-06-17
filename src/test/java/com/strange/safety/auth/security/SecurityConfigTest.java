package com.strange.safety.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class SecurityConfigTest {

    @Test
    void corsConfigurationUsesConfiguredAllowedOrigins() {
        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins("http://localhost:5173, https://front.example.com ");
        SecurityConfig securityConfig = new SecurityConfig(
                mock(JwtAuthenticationFilter.class),
                mock(RestAuthenticationEntryPoint.class),
                mock(RestAccessDeniedHandler.class),
                corsProperties
        );

        CorsConfiguration configuration = securityConfig.corsConfigurationSource()
                .getCorsConfiguration(new MockHttpServletRequest("POST", "/api/auth/login"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://front.example.com");
        assertThat(configuration.getAllowCredentials()).isTrue();
    }
}
