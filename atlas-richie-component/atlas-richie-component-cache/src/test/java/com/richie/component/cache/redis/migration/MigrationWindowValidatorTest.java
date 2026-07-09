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
package com.richie.component.cache.redis.migration;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MigrationWindowValidatorTest {

    @Test
    void runValidation_whenNoViolations_passes() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatCode(() -> validator.runValidation(() -> LocalDate.of(2026, 11, 30)))
                .doesNotThrowAnyException();
    }

    @Test
    void runValidation_whenExpiredViolations_throws() {
        AtlasRedisProperties props = new AtlasRedisProperties();
        props.getPerf().setEnabled(false);
        MigrationWindowValidator validator = new MigrationWindowValidator(props);

        assertThatThrownBy(() -> validator.runValidation(() -> LocalDate.of(2026, 12, 2)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Migration windows expired");
    }

    @Test
    void runValidation_whenPropertiesNull_isNoOp() {
        MigrationWindowValidator validator = new MigrationWindowValidator(null);

        assertThatCode(() -> validator.runValidation(LocalDate::now)).doesNotThrowAnyException();
    }
}
