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
package com.richie.component.storage.observability;

import com.richie.component.storage.enums.StorageEngineEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class StorageEngineMetricsTest {

    private StorageEngineMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new StorageEngineMetrics();
    }

    @Test
    void constructor_shouldInitializeCountersForAllEnumValues() {
        for (StorageEngineEnum type : StorageEngineEnum.values()) {
            assertThat(metrics.switchCount(type)).isNotNull();
            assertThat(metrics.registerCount(type)).isNotNull();
            assertThat(metrics.switchCount(type).get()).isZero();
            assertThat(metrics.registerCount(type).get()).isZero();
        }
    }

    @Test
    void incrementSwitch_shouldIncrementForSpecificType() {
        metrics.incrementSwitch(StorageEngineEnum.MINIO);
        metrics.incrementSwitch(StorageEngineEnum.MINIO);
        metrics.incrementSwitch(StorageEngineEnum.MINIO);

        metrics.incrementSwitch(StorageEngineEnum.FTP);

        assertThat(metrics.switchCount(StorageEngineEnum.MINIO).get()).isEqualTo(3);
        assertThat(metrics.switchCount(StorageEngineEnum.FTP).get()).isEqualTo(1);
        assertThat(metrics.switchCount(StorageEngineEnum.SFTP).get()).isZero();
    }

    @Test
    void incrementRegister_shouldIncrementForSpecificType() {
        metrics.incrementRegister(StorageEngineEnum.MINIO);

        assertThat(metrics.registerCount(StorageEngineEnum.MINIO).get()).isEqualTo(1);
        assertThat(metrics.switchCount(StorageEngineEnum.MINIO).get())
                .as("register and switch counters are independent").isZero();
    }

    @Test
    void switchCountAndRegisterCount_shouldReturnSameAtomicLongReferences() {
        AtomicLong first = metrics.switchCount(StorageEngineEnum.MINIO);
        AtomicLong second = metrics.switchCount(StorageEngineEnum.MINIO);
        assertThat(first).isSameAs(second);
    }
}
