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
package com.richie.component.cache.commons;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyUtilsTest {

    @ParameterizedTest
    @CsvSource({
            "plain-key, plain-key",
            "shard@@real-key, real-key",
            "a@@b@@c, b"
    })
    void getRealKey_stripsShardPrefix(String input, String expected) {
        assertThat(CacheKeyUtils.getRealKey(input)).isEqualTo(expected);
    }

    @Test
    void getRealKeys_mapsAllKeys() {
        List<String> keys = List.of("k1", "shard@@k2", "x@@y@@z");
        assertThat(CacheKeyUtils.getRealKeys(keys)).containsExactly("k1", "k2", "y");
    }
}
