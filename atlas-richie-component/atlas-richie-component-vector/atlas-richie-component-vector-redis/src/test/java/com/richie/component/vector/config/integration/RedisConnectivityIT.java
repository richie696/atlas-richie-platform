package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.support.AbstractVectorRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConnectivityIT extends AbstractVectorRedisIntegrationTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void redis_shouldPing() {
        stringRedisTemplate.opsForValue().set("it:ping", "pong");
        assertThat(stringRedisTemplate.opsForValue().get("it:ping")).isEqualTo("pong");
    }
}
