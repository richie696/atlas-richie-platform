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
