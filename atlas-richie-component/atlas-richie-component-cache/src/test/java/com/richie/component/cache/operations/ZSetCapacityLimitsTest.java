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
package com.richie.component.cache.operations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ZSetCapacityLimitsTest {

    @Test
    void thresholds_matchReadmeGuidance() {
        assertThat(ZSetCapacityLimits.ZSET_RECOMMENDED_MAX_ELEMENTS).isEqualTo(5_000L);
        assertThat(ZSetCapacityLimits.ZSET_HARD_MAX_ELEMENTS).isEqualTo(10_000L);
    }

    @Test
    void exceedsRecommended_at5000() {
        assertThat(ZSetCapacityLimits.exceedsRecommended(4_999L)).isFalse();
        assertThat(ZSetCapacityLimits.exceedsRecommended(5_000L)).isTrue();
    }

    @Test
    void exceedsHardLimit_at10000() {
        assertThat(ZSetCapacityLimits.exceedsHardLimit(9_999L)).isFalse();
        assertThat(ZSetCapacityLimits.exceedsHardLimit(10_000L)).isTrue();
    }
}
