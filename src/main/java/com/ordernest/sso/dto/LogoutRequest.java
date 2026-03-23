package com.ordernest.sso.dto;

public record LogoutRequest(
    String refreshToken,
    String accessToken
) {
}
