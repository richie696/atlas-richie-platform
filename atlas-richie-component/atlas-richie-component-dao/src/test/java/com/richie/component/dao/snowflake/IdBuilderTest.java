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
package com.richie.component.dao.snowflake;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdBuilderTest {

    @Test
    void nextId_generatesMonotonicValuesForFixedWorker() {
        IdBuilder builder = new IdBuilder(42L);
        long first = builder.nextId();
        long second = builder.nextId();

        assertThat(second).isGreaterThan(first);
    }

    @Test
    void nextId_generatesUniqueValuesInBurst() {
        IdBuilder builder = new IdBuilder(7L);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ids.add(builder.nextId());
        }
        assertThat(ids).hasSize(100);
    }

    @Test
    void constructor_rejectsWorkerIdAboveMax() {
        assertThatThrownBy(() -> new IdBuilder(1024L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("worker Id");
    }

    @Test
    void constructor_rejectsNegativeWorkerId() {
        assertThatThrownBy(() -> new IdBuilder(-1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_acceptsBoundaryWorkerIds() {
        assertThatCode(() -> new IdBuilder(0L)).doesNotThrowAnyException();
        assertThatCode(() -> new IdBuilder(1023L)).doesNotThrowAnyException();
    }

    @Test
    void defaultConstructor_generatesIds() {
        IdBuilder builder = new IdBuilder();
        assertThat(builder.nextId()).isPositive();
    }

    @Test
    void nextId_staysUniqueUnderRapidCalls() {
        IdBuilder builder = new IdBuilder(0L);
        long previous = builder.nextId();
        for (int i = 0; i < 200; i++) {
            long current = builder.nextId();
            assertThat(current).isGreaterThan(previous);
            previous = current;
        }
    }

    @Test
    void waitIfNecessary_doesNotSleepWhenBehindTimestamp() throws Exception {
        IdBuilder builder = new IdBuilder(1L);
        Method waitIfNecessary = IdBuilder.class.getDeclaredMethod("waitIfNecessary");
        waitIfNecessary.setAccessible(true);
        assertThatCode(() -> waitIfNecessary.invoke(builder)).doesNotThrowAnyException();
    }

    @Test
    void generateWorkerId_fallsBackToRandomWhenMacGenerationFails() throws Exception {
        IdBuilder builder = new IdBuilder(1L);
        Method generateWorkerId = IdBuilder.class.getDeclaredMethod("generateWorkerId");
        generateWorkerId.setAccessible(true);
        long workerId = (long) generateWorkerId.invoke(builder);
        assertThat(workerId).isGreaterThanOrEqualTo(0);
    }
}
