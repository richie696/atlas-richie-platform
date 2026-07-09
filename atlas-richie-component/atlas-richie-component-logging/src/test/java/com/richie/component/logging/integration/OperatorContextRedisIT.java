/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.logging.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.GlobalCacheManager;
import com.richie.component.logging.domain.OperatorInfo;
import com.richie.component.logging.handler.OperatorContextHolder;
import com.richie.component.logging.support.LoggingRedisIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@LoggingRedisIntegrationTest
class OperatorContextRedisIT {

    @Autowired
    private GlobalCacheManager cacheManager;

    @BeforeEach
    void wireGlobalCache() throws Exception {
        Field field = GlobalCache.class.getDeclaredField("DELEGATE");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        AtomicReference<GlobalCacheManager> ref = (AtomicReference<GlobalCacheManager>) field.get(null);
        ref.set(cacheManager);
    }

    @Test
    void operatorContext_roundTripInRedis() {
        OperatorContextHolder.setOperator("it-token", "op-1", "Tester", 60_000L);

        assertThat(OperatorContextHolder.hasOperator("it-token")).isTrue();
        OperatorInfo operator = OperatorContextHolder.getOperator("it-token");
        assertThat(operator.getId()).isEqualTo("op-1");
        assertThat(operator.getName()).isEqualTo("Tester");
    }
}
