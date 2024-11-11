package com.ordernest.sso.service;

import org.springframework.stereotype.Service;
import redis.clients.jedis.UnifiedJedis;

@Service
public class RateLimitService {

    private final UnifiedJedis jedis;

    public RateLimitService(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    public boolean incrementAndCheck(String key, int maxCount, int windowSeconds) {
        long current = jedis.incr(key);
        if (current == 1L && windowSeconds > 0) {
            jedis.expire(key, windowSeconds);
        }
        return current <= maxCount;
    }
}
