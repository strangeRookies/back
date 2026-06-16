package com.strange.safety.auth.security;

import com.strange.safety.common.exception.CustomException;
import com.strange.safety.common.exception.ErrorCode;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenCookieManager {

    private final RefreshTokenCookieProperties properties;
    private final JwtProperties jwtProperties;

    public RefreshTokenCookieManager(RefreshTokenCookieProperties properties, JwtProperties jwtProperties) {
        this.properties = properties;
        this.jwtProperties = jwtProperties;
    }

    public String extract(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
        }
        for (Cookie cookie : cookies) {
            if (properties.getName().equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        throw new CustomException(ErrorCode.AUTH_INVALID_TOKEN);
    }

    public void add(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(refreshToken, maxAge()).toString());
    }

    public void expire(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(properties.getName(), value)
                .httpOnly(true)
                .secure(properties.isSecure())
                .sameSite(properties.getSameSite())
                .path(properties.getPath())
                .maxAge(maxAge)
                .build();
    }

    private Duration maxAge() {
        return Duration.ofMillis(jwtProperties.getRefreshTokenExpirationMs());
    }
}
