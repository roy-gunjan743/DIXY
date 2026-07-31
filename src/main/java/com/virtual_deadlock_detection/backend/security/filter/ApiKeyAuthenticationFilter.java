package com.virtual_deadlock_detection.backend.security.filter;

import com.virtual_deadlock_detection.backend.security.ApiKeyProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates the {@code X-API-Key} request header against the configured key
 * (security.api-key) and, if it matches, marks the request as authenticated
 * so downstream authorization rules in SecurityConfig can allow it through.
 *
 * If the header is missing or wrong, this filter does nothing — it leaves the
 * request unauthenticated and lets Spring Security's access-denied handling
 * (see AuthenticationEntryPoint) reject it with 401.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";

    private final ApiKeyProperties apiKeyProperties;

    public ApiKeyAuthenticationFilter(ApiKeyProperties apiKeyProperties) {
        this.apiKeyProperties = apiKeyProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String providedKey = request.getHeader(API_KEY_HEADER);
        String expectedKey = apiKeyProperties.getApiKey();

        if (providedKey != null && expectedKey != null && !expectedKey.isBlank()
                && constantTimeEquals(providedKey, expectedKey)) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "api-client", null, List.of(new SimpleGrantedAuthority("ROLE_API_CLIENT")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Compares two strings in constant time, so a wrong key doesn't leak
     * timing information about how many leading characters matched.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}