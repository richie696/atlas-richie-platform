package com.richie.component.mfa.core.crypto.integration;

import com.richie.component.mfa.core.crypto.support.AbstractMfaRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectivityIT extends AbstractMfaRedisIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void redis_shouldPing() {
        stringRedisTemplate.opsForValue().set("it:ping", "pong");
        assertThat(stringRedisTemplate.opsForValue().get("it:ping")).isEqualTo("pong");
    }
}
