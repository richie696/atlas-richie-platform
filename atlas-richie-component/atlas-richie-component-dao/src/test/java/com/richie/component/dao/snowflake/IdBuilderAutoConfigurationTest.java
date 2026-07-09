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
package com.richie.component.dao.snowflake;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdBuilderAutoConfigurationTest {

    @Test
    void idBuilder_returnsValidBuilderWhenRedisReturnsWorkerId() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(42L);

        IdBuilderAutoConfiguration autoConfig = new IdBuilderAutoConfiguration(redisTemplate);
        IdBuilder idBuilder = autoConfig.idBuilder();

        assertThat(idBuilder).isNotNull();
        assertThat(idBuilder.nextId()).isPositive();
    }

    @Test
    void idBuilder_fallsBackToRandomWhenRedisReturnsNull() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(null);

        IdBuilderAutoConfiguration autoConfig = new IdBuilderAutoConfiguration(redisTemplate);
        IdBuilder idBuilder = autoConfig.idBuilder();

        assertThat(idBuilder).isNotNull();
        assertThat(idBuilder.nextId()).isPositive();
    }

    @Test
    void idBuilder_generatesUniqueIdsAcrossMultipleCalls() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList())).thenReturn(7L);

        IdBuilderAutoConfiguration autoConfig = new IdBuilderAutoConfiguration(redisTemplate);
        IdBuilder idBuilder = autoConfig.idBuilder();

        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(idBuilder.nextId());
        }
        assertThat(ids).hasSize(100);
    }
}
