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
package com.richie.component.web.core.degrade;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Trigger} 枚举值完整性测试。
 */
class TriggerTest {

    @Test
    void enumValues_count() {
        assertThat(Trigger.values()).hasSize(3);
    }

    @Test
    void enumValues_names() {
        assertThat(Trigger.EXCEPTION.name()).isEqualTo("EXCEPTION");
        assertThat(Trigger.HIGH_LATENCY.name()).isEqualTo("HIGH_LATENCY");
        assertThat(Trigger.CUSTOM.name()).isEqualTo("CUSTOM");
    }

    @Test
    void valueOf_roundtrip() {
        for (Trigger t : Trigger.values()) {
            assertThat(Trigger.valueOf(t.name())).isSameAs(t);
        }
    }
}