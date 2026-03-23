package com.ordernest.sso.dto;

public record RefreshRequest(
    String refreshToken,
    String deviceId
) {
}
