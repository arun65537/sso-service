package com.ordernest.sso.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

@Service
public class JwtBlacklistService {

    private final UnifiedJedis jedis;

    public JwtBlacklistService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    public void blacklist(String jti, Duration ttl) {
        if (jti == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        long ttlSeconds = Math.max(1L, ttl.getSeconds());
        jedis.set(key(jti), "1", SetParams.setParams().ex(ttlSeconds));
    }

    public boolean isBlacklisted(String jti) {
        return jedis.exists(key(jti));
    }

    private String key(String jti) {
        return "blacklist:access:" + jti;
    }
}
