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
package com.richie.component.cache.redis.migration;

import com.richie.context.migration.MigrationWindow;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationViolationTest {

    @Test
    void describe_containsKeyFields() throws Exception {
        Field field = SampleFlags.class.getDeclaredField("enabled");
        MigrationWindow window = field.getAnnotation(MigrationWindow.class);
        MigrationViolation violation = new MigrationViolation(
                SampleFlags.class,
                field,
                window,
                false,
                LocalDate.of(2026, 12, 1),
                LocalDate.of(2026, 12, 2));

        assertThat(violation.describe())
                .contains("SampleFlags.enabled")
                .contains("until=2026-12-01")
                .contains("now=2026-12-02");
    }

    @Test
    void validateShape_rejectsNonBooleanField() throws Exception {
        Field field = SampleFlags.class.getDeclaredField("notFlag");
        MigrationWindow window = field.getAnnotation(MigrationWindow.class);
        MigrationViolation violation = new MigrationViolation(
                SampleFlags.class,
                field,
                window,
                false,
                LocalDate.now(),
                LocalDate.now());

        assertThatThrownBy(violation::validateShape)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boolean fields");
    }

    static class SampleFlags {
        @MigrationWindow(until = "2026-12-01", removedIn = "1.0.0", owner = "t", reason = "r")
        boolean enabled;

        @MigrationWindow(until = "2026-12-01", removedIn = "1.0.0", owner = "t", reason = "r")
        String notFlag;
    }
}
