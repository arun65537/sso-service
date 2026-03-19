package com.ordernest.sso.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Jwt jwt = new Jwt();
    private final Security security = new Security();
    private final Verification verification = new Verification();
    private final PasswordReset passwordReset = new PasswordReset();

    public Jwt getJwt() { return jwt; }
    public Security getSecurity() { return security; }
    public Verification getVerification() { return verification; }
    public PasswordReset getPasswordReset() { return passwordReset; }

    public static class Jwt {
        private String issuer;
        private long accessTokenMinutes;
        private long refreshTokenDays;
        private String keyId;

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public long getAccessTokenMinutes() { return accessTokenMinutes; }
        public void setAccessTokenMinutes(long accessTokenMinutes) { this.accessTokenMinutes = accessTokenMinutes; }
        public long getRefreshTokenDays() { return refreshTokenDays; }
        public void setRefreshTokenDays(long refreshTokenDays) { this.refreshTokenDays = refreshTokenDays; }
        public String getKeyId() { return keyId; }
        public void setKeyId(String keyId) { this.keyId = keyId; }
    }

    public static class Security {
        private int loginRateLimitCount;
        private int loginRateLimitWindowSeconds;

        public int getLoginRateLimitCount() { return loginRateLimitCount; }
        public void setLoginRateLimitCount(int loginRateLimitCount) { this.loginRateLimitCount = loginRateLimitCount; }
        public int getLoginRateLimitWindowSeconds() { return loginRateLimitWindowSeconds; }
        public void setLoginRateLimitWindowSeconds(int loginRateLimitWindowSeconds) { this.loginRateLimitWindowSeconds = loginRateLimitWindowSeconds; }
    }

    public static class Verification {
        private long tokenMinutes;
        private String baseUrl;
        private String path;

        public long getTokenMinutes() { return tokenMinutes; }
        public void setTokenMinutes(long tokenMinutes) { this.tokenMinutes = tokenMinutes; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    public static class PasswordReset {
        private long tokenMinutes;
        private String baseUrl;
        private String path;

        public long getTokenMinutes() { return tokenMinutes; }
        public void setTokenMinutes(long tokenMinutes) { this.tokenMinutes = tokenMinutes; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }
}
