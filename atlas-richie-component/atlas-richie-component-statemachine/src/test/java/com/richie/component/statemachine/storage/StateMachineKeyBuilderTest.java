/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.statemachine.storage;

import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineKeyBuilderTest {

    private StateMachineKeyBuilder keyBuilder;

    @BeforeEach
    void setUp() {
        StateMachineProperties properties = new StateMachineProperties();
        RedisStreamConfig redisStream = new RedisStreamConfig();
        redisStream.setKeyPrefix("platform:statemachine");
        properties.setRedisStream(redisStream);
        keyBuilder = new StateMachineKeyBuilder(properties);
    }

    @Test
    void buildCurrentStateKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildCurrentStateKey("order", 42L))
                .isEqualTo("platform:statemachine:state:order:42");
    }

    @Test
    void buildHistoryListKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildHistoryListKey("order", 42L))
                .isEqualTo("platform:statemachine:history:list:order:42");
    }

    @Test
    void buildSyncKey_shouldExcludePrefix() {
        assertThat(keyBuilder.buildSyncKey("order", 42L)).isEqualTo("order:42");
    }

    @Test
    void getKeyPrefix_shouldTrimTrailingColon() {
        StateMachineProperties properties = new StateMachineProperties();
        RedisStreamConfig redisStream = new RedisStreamConfig();
        redisStream.setKeyPrefix("custom:prefix:");
        properties.setRedisStream(redisStream);
        StateMachineKeyBuilder customBuilder = new StateMachineKeyBuilder(properties);

        assertThat(customBuilder.getKeyPrefix()).isEqualTo("custom:prefix");
        assertThat(customBuilder.buildDbSyncStreamKey()).isEqualTo("custom:prefix:db:sync");
    }

    @Test
    void getKeyPrefix_shouldReturnDefaultWhenNull() {
        StateMachineProperties properties = new StateMachineProperties();
        RedisStreamConfig redisStream = new RedisStreamConfig();
        redisStream.setKeyPrefix(null);
        properties.setRedisStream(redisStream);
        StateMachineKeyBuilder customBuilder = new StateMachineKeyBuilder(properties);

        assertThat(customBuilder.getKeyPrefix()).isEqualTo("platform:statemachine");
    }

    @Test
    void buildHistoryKey_shouldUseCreateTimeEpoch() {
        LocalDateTime createTime = LocalDateTime.ofEpochSecond(1700000000L, 0, ZoneOffset.UTC);
        assertThat(keyBuilder.buildHistoryKey("order", 42L, createTime))
                .isEqualTo("platform:statemachine:history:order:42:1700000000");
    }

    @Test
    void buildHistoryKey_shouldFallbackToCurrentTimeMillisWhenCreateTimeIsNull() {
        long before = System.currentTimeMillis();
        String key = keyBuilder.buildHistoryKey("order", 42L, null);
        long after = System.currentTimeMillis();

        assertThat(key).startsWith("platform:statemachine:history:order:42:");
        long timestamp = Long.parseLong(key.substring("platform:statemachine:history:order:42:".length()));
        assertThat(timestamp).isBetween(before, after);
    }

    @Test
    void buildDbSyncQueueKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildDbSyncQueueKey())
                .isEqualTo("platform:statemachine:db:sync:queue");
    }

    @Test
    void buildDbSyncSetKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildDbSyncSetKey())
                .isEqualTo("platform:statemachine:db:sync:set");
    }

    @Test
    void buildDbSyncLockKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildDbSyncLockKey())
                .isEqualTo("platform:statemachine:db:sync:lock");
    }

    @Test
    void buildSeqKey_shouldFollowConvention() {
        assertThat(keyBuilder.buildSeqKey("order", 42L))
                .isEqualTo("platform:statemachine:seq:order:42");
    }
}
