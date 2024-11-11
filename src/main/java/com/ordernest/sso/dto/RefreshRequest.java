package com.ordernest.sso.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank String refreshToken,
    String deviceId
) {
}
