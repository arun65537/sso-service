package com.ordernest.sso.repository;

import com.ordernest.sso.model.RefreshToken;
import com.ordernest.sso.model.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);
    List<RefreshToken> findAllByUserAndRevokedFalse(User user);
    long deleteByExpiresAtBefore(Instant timestamp);
}
