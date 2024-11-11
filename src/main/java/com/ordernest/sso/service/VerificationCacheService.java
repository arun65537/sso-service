package com.ordernest.sso.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

@Service
public class VerificationCacheService {

    private final UnifiedJedis jedis;

    public VerificationCacheService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    public void cacheVerificationToken(String token, String userId, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl.getSeconds());
        jedis.set("verify:" + token, userId, SetParams.setParams().ex(ttlSeconds));
    }

    public String getUserIdForToken(String token) {
        return jedis.get("verify:" + token);
    }

    public void deleteToken(String token) {
        jedis.del("verify:" + token);
    }
}
