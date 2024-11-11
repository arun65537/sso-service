package com.ordernest.sso.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.UnifiedJedis;

@Configuration
public class RedisConfig {

    @Bean(destroyMethod = "close")
    public UnifiedJedis unifiedJedis(
        @Value("${spring.data.redis.host}") String host,
        @Value("${spring.data.redis.port}") int port,
        @Value("${spring.data.redis.username:}") String username,
        @Value("${spring.data.redis.password:}") String password
    ) {
        DefaultJedisClientConfig.Builder builder = DefaultJedisClientConfig.builder();
        if (username != null && !username.isBlank()) {
            builder.user(username);
        }
        if (password != null && !password.isBlank()) {
            builder.password(password);
        }

        JedisClientConfig config = builder.build();
        return new UnifiedJedis(new HostAndPort(host, port), config);
    }
}
