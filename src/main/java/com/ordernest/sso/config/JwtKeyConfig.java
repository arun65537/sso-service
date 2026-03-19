package com.ordernest.sso.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtKeyConfig {
    @Value("${app.jwt.key-id}")
    private String keyId;

    @Value("${app.jwt.private-key-base64}")
    private String privateKeyBase64;

    @Value("${app.jwt.public-key-base64}")
    private String publicKeyBase64;

    @Bean
    public RSAKey rsaKey() {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64))
            );
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))
            );
            KeyPair pair = new KeyPair(publicKey, privateKey);

            return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(keyId)
                .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize RSA key pair", ex);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(RSAKey rsaKey) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey) {
        try {
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize JWT decoder", ex);
        }
    }
}
