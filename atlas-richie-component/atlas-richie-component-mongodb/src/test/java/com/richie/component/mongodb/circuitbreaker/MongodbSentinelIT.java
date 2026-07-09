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
package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.Mongodb;
import com.richie.component.mongodb.support.MongodbIntegrationTest;
import com.richie.component.mongodb.support.MongodbIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@MongodbIntegrationTest
class MongodbSentinelIT {

    private static final boolean ENABLED = MongodbIntegrationTestSupport.getInstance().isEnabled();

    private Mongodb mongodb;

    @BeforeEach
    void setUp() {
        mongodb = new Mongodb(null, null, null, null, null, null, null);
    }

    @Test
    void query_fallbackReturnsEmptyList() {
        List<?> result = DefaultFallbacks.query(String.class);
        assertThat(result).isEmpty();
    }

    @Test
    void updateExecute_fallbackReturnsZero() {
        long result = DefaultFallbacks.updateExecute();
        assertThat(result).isZero();
    }

    @Test
    void deleteExecute_fallbackReturnsZero() {
        long result = DefaultFallbacks.deleteExecute();
        assertThat(result).isZero();
    }

    @Test
    void insertAll_fallbackReturnsEmptyList() {
        List<?> result = DefaultFallbacks.insertAll();
        assertThat(result).isEmpty();
    }

    @Test
    void findById_fallbackReturnsEmptyOptional() {
        var result = DefaultFallbacks.findById();
        assertThat(result).isEmpty();
    }

    @Test
    void existsById_fallbackReturnsFalse() {
        boolean result = DefaultFallbacks.existsById();
        assertThat(result).isFalse();
    }

    @Test
    void deleteById_fallbackReturnsFalse() {
        boolean result = DefaultFallbacks.deleteById();
        assertThat(result).isFalse();
    }

    @Test
    void dropCollection_fallbackReturnsFalse() {
        boolean result = DefaultFallbacks.dropCollection();
        assertThat(result).isFalse();
    }

    @Test
    void queryBuilder_fallbackReturnsEmptyOnList() {
        var builder = DefaultFallbacks.queryBuilder(String.class);
        assertThat(builder.list()).isEmpty();
    }

    @Test
    void updateBuilder_fallbackReturnsZeroOnExecute() {
        var builder = DefaultFallbacks.updateBuilder(String.class);
        assertThat(builder.execute()).isZero();
    }

    @Test
    void deleteBuilder_fallbackReturnsZeroOnExecute() {
        var builder = DefaultFallbacks.deleteBuilder(String.class);
        assertThat(builder.execute()).isZero();
    }
}
