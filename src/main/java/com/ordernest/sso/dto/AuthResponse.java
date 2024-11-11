package com.ordernest.sso.dto;

import java.util.Set;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    Set<String> roles
) {
}
