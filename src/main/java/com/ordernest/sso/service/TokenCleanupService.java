package com.ordernest.sso.service;

import com.ordernest.sso.repository.RefreshTokenRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TokenCleanupService {

    private final RefreshTokenRepository refreshTokenRepository;

    public TokenCleanupService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupExpiredRefreshTokens() {
        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
