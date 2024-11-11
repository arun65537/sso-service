package com.ordernest.sso.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String refreshToken,
    String accessToken
) {
}
