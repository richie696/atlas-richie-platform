package com.richie.component.redis.streammq.stream;

import com.richie.component.cache.redis.bean.MultiRedisTemplate;
import com.richie.component.redis.streammq.config.stream.RedisStreamIdempotencyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisStreamIdempotencyGuardTest {

    private RedisStreamIdempotencyGuard guard;

    @BeforeEach
    void setUp() {
        @SuppressWarnings("unchecked")
        MultiRedisTemplate<Object> redisTemplate = mock(MultiRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<Object, Object> valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true, false);

        RedisStreamIdempotencyProperties properties = new RedisStreamIdempotencyProperties();
        properties.setKeyPrefix("test:idemp:");
        guard = new RedisStreamIdempotencyGuard(redisTemplate, properties);
    }

    @Test
    void tryAcquire_returnsTrueOnFirstAttempt() {
        assertThat(guard.tryAcquire("order-1", Duration.ofMinutes(5))).isTrue();
    }

    @Test
    void tryAcquire_returnsFalseOnDuplicate() {
        assertThat(guard.tryAcquire("order-2", Duration.ofMinutes(5))).isTrue();
        assertThat(guard.tryAcquire("order-2", Duration.ofMinutes(5))).isFalse();
    }
}
