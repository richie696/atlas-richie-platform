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
package com.richie.component.mongodb.observability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class MongodbSlowQueryLoggerTest {

    private MongodbSlowQueryLogger logger;

    @BeforeEach
    void setUp() {
        logger = new MongodbSlowQueryLogger();
    }

    @Test
    void logIfSlow_belowThreshold_shouldNotLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold - 1);
    }

    @Test
    void logIfSlow_atThreshold_shouldNotLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold);
    }

    @Test
    void logIfSlow_aboveThreshold_shouldLog() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        logger.logIfSlow("users", "find", threshold + 1);
    }

    @Test
    void threshold_shouldBe200Ms() throws Exception {
        Field thresholdField = MongodbSlowQueryLogger.class.getDeclaredField("THRESHOLD_MS");
        thresholdField.setAccessible(true);
        long threshold = (long) thresholdField.get(null);

        assertThat(threshold).isEqualTo(200L);
    }
}
