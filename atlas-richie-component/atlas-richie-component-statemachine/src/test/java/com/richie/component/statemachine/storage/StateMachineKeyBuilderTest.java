package com.richie.component.statemachine.storage;

import com.richie.component.statemachine.config.StateMachineProperties;
import com.richie.component.statemachine.config.properties.RedisStreamConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
