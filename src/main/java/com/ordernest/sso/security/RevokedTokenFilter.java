package com.ordernest.sso.security;

import com.ordernest.sso.service.JwtBlacklistService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RevokedTokenFilter extends OncePerRequestFilter {

    private final JwtDecoder jwtDecoder;
    private final JwtBlacklistService blacklistService;

    public RevokedTokenFilter(JwtDecoder jwtDecoder, JwtBlacklistService blacklistService) {
        this.jwtDecoder = jwtDecoder;
        this.blacklistService = blacklistService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Jwt jwt = jwtDecoder.decode(token);
                String jti = jwt.getId();
                if (jti != null && blacklistService.isBlacklisted(jti)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.getWriter().write("{\"message\":\"Token revoked\"}");
                    return;
                }
            } catch (Exception ignored) {
                // Let resource server process malformed/invalid token.
            }
        }
        filterChain.doFilter(request, response);
    }
}
