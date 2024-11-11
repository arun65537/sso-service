package com.ordernest.sso.controller;

import com.ordernest.sso.dto.AuthResponse;
import com.ordernest.sso.dto.LoginRequest;
import com.ordernest.sso.dto.LogoutRequest;
import com.ordernest.sso.dto.MessageResponse;
import com.ordernest.sso.dto.PasswordResetConfirmRequest;
import com.ordernest.sso.dto.PasswordResetRequest;
import com.ordernest.sso.dto.RefreshRequest;
import com.ordernest.sso.dto.RegisterRequest;
import com.ordernest.sso.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<MessageResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        authService.register(request, http.getRemoteAddr(), http.getHeader("User-Agent"));
        return ResponseEntity.ok(new MessageResponse("Registration successful. Check your email for the verification link."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, http.getRemoteAddr(), http.getHeader("User-Agent")));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken(), request.accessToken());
        return ResponseEntity.ok(new MessageResponse("Logged out"));
    }

    @GetMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
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
}
