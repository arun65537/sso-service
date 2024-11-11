package com.ordernest.sso.jwt;

import com.ordernest.sso.config.AppProperties;
import com.ordernest.sso.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final AppProperties appProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtDecoder jwtDecoder, AppProperties appProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.appProperties = appProperties;
    }

    public AccessTokenDetails generateAccessToken(User user, Set<String> roles) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(appProperties.getJwt().getAccessTokenMinutes(), ChronoUnit.MINUTES);
        String jti = UUID.randomUUID().toString();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(appProperties.getJwt().getIssuer())
            .subject(user.getId().toString())
            .id(jti)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .claim("userId", user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .build();

        JwsHeader header = JwsHeader.with(() -> "RS256")
            .keyId(appProperties.getJwt().getKeyId())
            .build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessTokenDetails(token, jti, issuedAt, expiresAt);
    }

    public Jwt decode(String token) {
        return jwtDecoder.decode(token);
    }

    public record AccessTokenDetails(String token, String jti, Instant issuedAt, Instant expiresAt) {}
}
