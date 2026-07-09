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
package com.richie.component.web.core.protection;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LongLivedPathBypassTest {

    @Test
    void matches_ssePath() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(
                List.of("/ws/**", "/sse/**", "/stream/**"));
        assertThat(bypass.matches("/sse/events")).isTrue();
        assertThat(bypass.matches("/ws/chat")).isTrue();
        assertThat(bypass.matches("/stream/logs")).isTrue();
    }

    @Test
    void matches_nestedPaths() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.matches("/sse/api/v1/events/123")).isTrue();
    }

    @Test
    void doesNotMatch_nonListedPath() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.matches("/api/v1/users")).isFalse();
        assertThat(bypass.matches("/rest/sse-prefix-mimic")).isFalse();
    }

    @Test
    void emptyPatterns_neverMatches() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of());
        assertThat(bypass.matches("/sse/events")).isFalse();
        assertThat(bypass.matches("/")).isFalse();
    }

    @Test
    void nullPath_doesNotMatch() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/**"));
        assertThat(bypass.matches(null)).isFalse();
    }

    @Test
    void patterns_returnsImmutableList() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/sse/**"));
        assertThat(bypass.patterns()).containsExactly("/sse/**");
    }

    @Test
    void constructor_rejectsNull() {
        assertThatThrownBy(() -> new LongLivedPathBypass(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void exactMatch_withoutWildcard() {
        LongLivedPathBypass bypass = new LongLivedPathBypass(List.of("/health"));
        assertThat(bypass.matches("/health")).isTrue();
        assertThat(bypass.matches("/health/")).isFalse();
    }
}