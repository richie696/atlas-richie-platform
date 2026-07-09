/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        MultiRedisTemplate<Object> redisTemplate = mock(MultiRedisTemplate.class);
        ValueOperations valueOps = mock(ValueOperations.class);
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
