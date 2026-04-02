package com.ordernest.sso.controller;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.dto.AuthResponse;
import com.ordernest.sso.dto.LoginRequest;
import com.ordernest.sso.dto.LogoutRequest;
import com.ordernest.sso.dto.MessageResponse;
import com.ordernest.sso.dto.PasswordResetConfirmRequest;
import com.ordernest.sso.dto.PasswordResetRequest;
import com.ordernest.sso.dto.RefreshRequest;
import com.ordernest.sso.dto.RegisterRequest;
import com.ordernest.sso.dto.UserProfileResponse;
import com.ordernest.sso.exception.BadRequestException;
import com.ordernest.sso.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final String REFRESH_TOKEN_COOKIE_NAME = "ordernest_refresh_token";

    private final AuthService authService;
    private final AppProperties appProperties;

    public AuthController(AuthService authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        authService.register(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(new MessageResponse("Registration successful. Check your email for the verification link."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
        @Valid @RequestBody LoginRequest request,
        HttpServletRequest http,
        HttpServletResponse response
    ) {
        AuthResponse authResponse = authService.login(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        setRefreshTokenCookie(response, authResponse.refreshToken(), http);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
        @Valid @RequestBody RefreshRequest request,
        HttpServletRequest http,
        HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(request.refreshToken(), http);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadRequestException("Refresh token is required");
        }

        AuthResponse authResponse = authService.refresh(new RefreshRequest(refreshToken, request.deviceId()));
        setRefreshTokenCookie(response, authResponse.refreshToken(), http);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(
        @Valid @RequestBody LogoutRequest request,
        HttpServletRequest http,
        HttpServletResponse response
    ) {
        String refreshToken = resolveRefreshToken(request.refreshToken(), http);
        authService.logout(refreshToken, request.accessToken());
        clearRefreshTokenCookie(response, http);
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.getCurrentUserProfile(jwt));
    }

    @PostMapping("/password-reset")
    public ResponseEntity<MessageResponse> passwordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("Password reset link sent to your email."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<MessageResponse> passwordResetConfirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.ok(new MessageResponse("Password updated successfully"));
    }

    private String resolveRefreshToken(String requestRefreshToken, HttpServletRequest request) {
        if (requestRefreshToken != null && !requestRefreshToken.isBlank()) {
            return requestRefreshToken;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (REFRESH_TOKEN_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, HttpServletRequest request) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }

        boolean secure = isHttps(request);
        String sameSite = secure ? "None" : "Lax";
        long refreshTtlSeconds = Duration.ofDays(appProperties.getJwt().getRefreshTokenDays()).getSeconds();

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, refreshToken)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(refreshTtlSeconds)
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshTokenCookie(HttpServletResponse response, HttpServletRequest request) {
        boolean secure = isHttps(request);
        String sameSite = secure ? "None" : "Lax";

        ResponseCookie cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(0)
            .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private boolean isHttps(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return forwardedProto.toLowerCase().contains("https");
        }
        return request.isSecure();
    }
}
