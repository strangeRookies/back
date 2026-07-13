package com.strange.safety.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

@Slf4j
public class VlmServiceTokenFilter extends OncePerRequestFilter {

    private final String expectedToken;

    public VlmServiceTokenFilter(String expectedToken) {
        this.expectedToken = expectedToken;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/api/internal/vlm/snapshot-assist/")) {
            String serviceToken = request.getHeader("X-Service-Token");

            if (expectedToken == null || expectedToken.isBlank() || serviceToken == null || serviceToken.isBlank() || !safeEquals(serviceToken, expectedToken)) {
                log.warn("[VLM-Security] Unauthorized access attempt to internal VLM endpoint: {}", path);
                response.setContentType("application/json");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"invalid service token\"}}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private boolean safeEquals(String a, String b) {
        try {
            return MessageDigest.isEqual(a.getBytes(), b.getBytes());
        } catch (Exception ex) {
            return false;
        }
    }
}
