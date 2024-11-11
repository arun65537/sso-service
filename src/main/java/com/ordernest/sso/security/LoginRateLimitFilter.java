package com.ordernest.sso.security;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final AppProperties appProperties;

    public LoginRateLimitFilter(RateLimitService rateLimitService, AppProperties appProperties) {
        this.rateLimitService = rateLimitService;
        this.appProperties = appProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if ("/auth/login".equals(request.getRequestURI()) && "POST".equalsIgnoreCase(request.getMethod())) {
            String key = "login:" + request.getRemoteAddr();
            boolean allowed = rateLimitService.incrementAndCheck(
                key,
                appProperties.getSecurity().getLoginRateLimitCount(),
                appProperties.getSecurity().getLoginRateLimitWindowSeconds()
            );
            if (!allowed) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.getWriter().write("{\"message\":\"Too many login attempts\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
