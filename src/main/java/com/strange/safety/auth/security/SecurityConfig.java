package com.strange.safety.auth.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@EnableConfigurationProperties({JwtProperties.class, RefreshTokenCookieProperties.class})
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                // 1. CORS 설정 활성화 추가 (csrf 바로 위에 추가)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) 
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/email-availability",
                                "/api/companies/business-number-availability"
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/signup/individual",
                                "/api/auth/signup/corporate",
                                "/api/auth/login",
                                "/api/auth/reissue",
                                "/api/auth/verifications/sms",
                                "/api/auth/verifications/sms/confirm",
                                "/api/auth/password-reset/verifications/sms",
                                "/api/auth/password-reset/verifications/sms/confirm",
                                "/api/auth/password-reset",
                                "/api/emergency-jurisdictions/resolve",
                                "/api/cameras/active"
                        ).permitAll()

                        // ADMIN 전용
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // USER 접근 가능
                        .requestMatchers(
                                "/api/facilities/**",
                                "/api/alert-events/**",
                                "/api/protected-targets/**",
                                "/api/emergency-contacts/**",
                                "/api/mypage/**",
                                "/api/cameras/**",
                                "/api/roi-configs/**"
                        ).hasAnyRole("ADMIN", "INDIVIDUAL", "CORPORATE")

                        // ADMIN 전용
                        .requestMatchers(
                                "/api/scenarios/**"
                        ).hasRole("ADMIN")

                        // 문의(Inquiry) 접근 제어
                        .requestMatchers(HttpMethod.POST, "/api/inquiries").hasAnyRole("INDIVIDUAL", "CORPORATE")
                        .requestMatchers("/api/inquiries/my").hasAnyRole("INDIVIDUAL", "CORPORATE")
                        .requestMatchers(HttpMethod.GET, "/api/inquiries/{inquiryId}").hasAnyRole("ADMIN", "INDIVIDUAL", "CORPORATE")
                        .requestMatchers(HttpMethod.GET, "/api/inquiries").hasRole("ADMIN")
                        .requestMatchers("/api/inquiries/*/answer").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    // 2. CORS 허용 규칙 설정 Bean 추가
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 프론트엔드 주소 허용 (Vite 기본 포트)
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        // 허용할 HTTP 메서드
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        // 허용할 헤더
        configuration.setAllowedHeaders(List.of("*"));
        // 쿠키, 인증 정보 허용
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 모든 경로("/**")에 대해 위 설정 적용
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
