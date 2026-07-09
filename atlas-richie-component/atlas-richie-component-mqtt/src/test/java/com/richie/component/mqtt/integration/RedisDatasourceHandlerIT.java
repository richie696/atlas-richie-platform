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
package com.richie.component.mqtt.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.mqtt.filter.datasource.impl.RedisDatasourceHandlerImpl;
import com.richie.component.mqtt.support.MqttRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@MqttRedisIntegrationTest
class RedisDatasourceHandlerIT {

    @Autowired
    private GlobalCacheManager cacheManager;

    @Autowired
    private RedisDatasourceHandlerImpl handler;

    @BeforeEach
    void wireGlobalCache() throws Exception {
        Field field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @Test
    void dedup_roundTripInRedis() {
        String hash = "it-hash-" + System.nanoTime();

        assertThat(handler.isDuplicate(hash)).isFalse();
        handler.saveCache(hash, 60_000L);
        assertThat(handler.isDuplicate(hash)).isTrue();

        assertThatThrownBy(() -> handler.saveCache(hash, 60_000L))
                .isInstanceOf(javax.management.openmbean.KeyAlreadyExistsException.class);
    }
}
