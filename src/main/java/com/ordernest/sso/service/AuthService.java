package com.ordernest.sso.service;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.dto.AuthResponse;
import com.ordernest.sso.dto.LoginRequest;
import com.ordernest.sso.dto.PasswordResetConfirmRequest;
import com.ordernest.sso.dto.PasswordResetRequest;
import com.ordernest.sso.dto.RefreshRequest;
import com.ordernest.sso.dto.RegisterRequest;
import com.ordernest.sso.dto.UserProfileResponse;
import com.ordernest.sso.exception.BadRequestException;
import com.ordernest.sso.exception.ConflictException;
import com.ordernest.sso.exception.UnauthorizedException;
import com.ordernest.sso.jwt.JwtTokenService;
import com.ordernest.sso.model.EmailVerificationToken;
import com.ordernest.sso.model.PasswordResetToken;
import com.ordernest.sso.model.RefreshToken;
import com.ordernest.sso.model.User;
import com.ordernest.sso.model.UserStatus;
import com.ordernest.sso.repository.EmailVerificationTokenRepository;
import com.ordernest.sso.repository.PasswordResetTokenRepository;
import com.ordernest.sso.repository.RefreshTokenRepository;
import com.ordernest.sso.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtBlacklistService jwtBlacklistService;
    private final SessionCacheService sessionCacheService;
    private final VerificationCacheService verificationCacheService;
    private final AuditService auditService;
    private final HttpNotificationService notificationService;
    private final AppProperties appProperties;

    public AuthService(
        UserRepository userRepository,
        RefreshTokenRepository refreshTokenRepository,
        EmailVerificationTokenRepository emailVerificationTokenRepository,
        PasswordResetTokenRepository passwordResetTokenRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        JwtBlacklistService jwtBlacklistService,
        SessionCacheService sessionCacheService,
        VerificationCacheService verificationCacheService,
        AuditService auditService,
        HttpNotificationService notificationService,
        AppProperties appProperties
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtBlacklistService = jwtBlacklistService;
        this.sessionCacheService = sessionCacheService;
        this.verificationCacheService = verificationCacheService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.appProperties = appProperties;
    }

    @Transactional
    public void register(RegisterRequest request, String ip, String userAgent) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(u -> {
            throw new ConflictException("Email is already registered");
        });

        User user = new User();
        user.setEmail(request.email().toLowerCase());
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.PENDING_VERIFICATION.name());
        user.setRoles(Set.of("ROLE_USER"));
        userRepository.save(user);

        String verificationToken = generateSecureToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUser(user);
        token.setToken(verificationToken);
        token.setExpiresAt(Instant.now().plus(appProperties.getVerification().getTokenMinutes(), ChronoUnit.MINUTES));
        token.setUsed(false);
        emailVerificationTokenRepository.save(token);

        try {
            verificationCacheService.cacheVerificationToken(
                verificationToken,
                user.getId().toString(),
                Duration.ofMinutes(appProperties.getVerification().getTokenMinutes())
            );
        } catch (Exception ex) {
            log.warn("Registration cache write failed for userId={}", user.getId(), ex);
        }

        try {
            notificationService.sendVerificationEmail(user.getEmail(), verificationToken);
        } catch (Exception ex) {
            log.warn("Verification email publish failed for userId={}, email={}", user.getId(), user.getEmail(), ex);
        }

        try {
            auditService.log(user, "REGISTER", ip, userAgent, "User registered");
        } catch (Exception ex) {
            log.warn("Audit log failed for register userId={}", user.getId(), ex);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request, String ip, String userAgent) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(user, "LOGIN_FAILED", ip, userAgent, "Invalid credentials");
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!UserStatus.ACTIVE.name().equals(user.getStatus())) {
            throw new UnauthorizedException("Account is not active. Verify email first.");
        }

        Set<String> roles = user.getRoles();
        JwtTokenService.AccessTokenDetails accessToken = jwtTokenService.generateAccessToken(user, roles);
        RefreshToken refreshToken = createRefreshToken(user, request.deviceId());

        sessionCacheService.putSession(
            refreshToken.getToken(),
            user.getId() + ":" + (request.deviceId() == null ? "unknown" : request.deviceId()),
            Duration.ofDays(appProperties.getJwt().getRefreshTokenDays())
        );

        auditService.log(user, "LOGIN_SUCCESS", ip, userAgent, "Login success");

        return new AuthResponse(
            accessToken.token(),
            refreshToken.getToken(),
            "Bearer",
            Duration.between(accessToken.issuedAt(), accessToken.expiresAt()).toSeconds(),
            roles
        );
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenRepository.findByToken(request.refreshToken())
            .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (existing.isRevoked() || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }

        existing.setRevoked(true);
        existing.setRevokedAt(Instant.now());

        User user = existing.getUser();
        Set<String> roles = user.getRoles();
        JwtTokenService.AccessTokenDetails accessToken = jwtTokenService.generateAccessToken(user, roles);

        RefreshToken rotated = createRefreshToken(user, request.deviceId() != null ? request.deviceId() : existing.getDeviceId());
        existing.setReplacedByToken(rotated.getToken());

        refreshTokenRepository.save(existing);
        sessionCacheService.removeSession(existing.getToken());
        sessionCacheService.putSession(
            rotated.getToken(),
            user.getId() + ":" + (rotated.getDeviceId() == null ? "unknown" : rotated.getDeviceId()),
            Duration.ofDays(appProperties.getJwt().getRefreshTokenDays())
        );

        return new AuthResponse(
            accessToken.token(),
            rotated.getToken(),
            "Bearer",
            Duration.between(accessToken.issuedAt(), accessToken.expiresAt()).toSeconds(),
            roles
        );
    }

    @Transactional
    public void logout(String refreshTokenValue, String accessTokenValue) {
        if (refreshTokenValue != null && !refreshTokenValue.isBlank()) {
            refreshTokenRepository.findByToken(refreshTokenValue).ifPresent(token -> {
                token.setRevoked(true);
                token.setRevokedAt(Instant.now());
                refreshTokenRepository.save(token);
            });
            sessionCacheService.removeSession(refreshTokenValue);
        }

        if (accessTokenValue != null && !accessTokenValue.isBlank()) {
            try {
                var jwt = jwtTokenService.decode(accessTokenValue);
                Duration ttl = Duration.between(Instant.now(), jwt.getExpiresAt());
                if (!ttl.isNegative() && !ttl.isZero()) {
                    jwtBlacklistService.blacklist(jwt.getId(), ttl);
                }
            } catch (Exception ex) {
                log.debug("Skipping access token blacklist during logout because token could not be decoded", ex);
            }
        }
    }

    @Transactional
    public void verifyEmail(String token) {
        EmailVerificationToken verificationToken = emailVerificationTokenRepository.findByToken(token)
            .orElseThrow(() -> new BadRequestException("Invalid verification token"));

        if (verificationToken.isUsed() || verificationToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Verification token is expired or already used");
        }

        User user = verificationToken.getUser();
        user.setStatus(UserStatus.ACTIVE.name());
        userRepository.save(user);

        verificationToken.setUsed(true);
        emailVerificationTokenRepository.save(verificationToken);
        verificationCacheService.deleteToken(token);
        notificationService.sendEmailVerifiedConfirmation(user.getEmail());
    }

    @Transactional
    public void requestPasswordReset(PasswordResetRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(() -> new BadRequestException("No user found for the given email"));

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setToken(generateSecureToken());
        token.setExpiresAt(Instant.now().plus(appProperties.getPasswordReset().getTokenMinutes(), ChronoUnit.MINUTES));
        token.setUsed(false);

        passwordResetTokenRepository.save(token);
        notificationService.sendPasswordResetEmail(user.getEmail(), token.getToken());
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByToken(request.token())
            .orElseThrow(() -> new BadRequestException("Invalid reset token"));

        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("Reset token is expired or already used");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        notificationService.sendPasswordChangedConfirmation(user.getEmail());
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(Jwt jwt) {
        if (jwt == null) {
            throw new UnauthorizedException("Missing authenticated user");
        }

        String userIdClaim = jwt.getClaimAsString("userId");
        String emailClaim = jwt.getClaimAsString("email");

        User user = null;
        if (userIdClaim != null && !userIdClaim.isBlank()) {
            try {
                UUID userId = UUID.fromString(userIdClaim.trim());
                user = userRepository.findById(userId).orElse(null);
            } catch (IllegalArgumentException ignored) {
                // Fallback to email lookup below.
            }
        }

        if (user == null && emailClaim != null && !emailClaim.isBlank()) {
            user = userRepository.findByEmailIgnoreCase(emailClaim.trim()).orElse(null);
        }

        if (user == null) {
            throw new UnauthorizedException("Authenticated user not found");
        }

        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getStatus(),
            user.getRoles()
        );
    }

    private RefreshToken createRefreshToken(User user, String deviceId) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(generateSecureToken());
        refreshToken.setDeviceId(deviceId);
        refreshToken.setExpiresAt(Instant.now().plus(appProperties.getJwt().getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshToken.setRevoked(false);
        return refreshTokenRepository.saveAndFlush(refreshToken);
    }

    private String generateSecureToken() {
        byte[] random = new byte[48];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}
