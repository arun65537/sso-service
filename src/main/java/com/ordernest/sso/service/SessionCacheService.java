package com.ordernest.sso.service;

import java.time.Duration;
import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.SetParams;

@Service
public class SessionCacheService {

    private final UnifiedJedis jedis;

    public SessionCacheService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    public void putSession(String refreshToken, String value, Duration ttl) {
        long ttlSeconds = Math.max(1L, ttl.getSeconds());
        jedis.set("session:" + refreshToken, value, SetParams.setParams().ex(ttlSeconds));
    }

    public void removeSession(String refreshToken) {
        jedis.del("session:" + refreshToken);
    }
}
