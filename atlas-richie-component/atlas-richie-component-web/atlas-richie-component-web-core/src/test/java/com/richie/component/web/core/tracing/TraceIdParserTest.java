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
package com.richie.component.web.core.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdParserTest {

    @Test
    void resolve_prefersW3CTraceparent() {
        String traceparent = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
        String resolved = TraceIdParser.resolve(traceparent, "fallback-request-id");
        assertThat(resolved).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void resolve_fallsBackToRequestId() {
        String resolved = TraceIdParser.resolve(null, "client-supplied-id");
        assertThat(resolved).hasSize(32);
        assertThat(resolved).startsWith("client-supplied-id");
    }

    @Test
    void resolve_generatesRandomWhenBothMissing() {
        String resolved = TraceIdParser.resolve(null, null);
        assertThat(resolved).hasSize(32);
        assertThat(resolved).matches("[0-9a-f]{32}");
    }

    @Test
    void resolve_generatesRandomWhenBothBlank() {
        String resolved = TraceIdParser.resolve("   ", "");
        assertThat(resolved).hasSize(32);
        assertThat(resolved).matches("[0-9a-f]{32}");
    }

    @Test
    void parseTraceparent_invalidVersion_returnsNull() {
        assertThat(TraceIdParser.parseTraceparent("01-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")).isNull();
    }

    @Test
    void parseTraceparent_invalidTraceId_returnsNull() {
        assertThat(TraceIdParser.parseTraceparent("00-XX-00f067aa0ba902b7-01")).isNull();
    }

    @Test
    void parseTraceparent_tooShort_returnsNull() {
        assertThat(TraceIdParser.parseTraceparent("00-abc-00f067aa0ba902b7-01")).isNull();
    }

    @Test
    void parseTraceparent_valid_returnsLowercase() {
        assertThat(TraceIdParser.parseTraceparent("00-4BF92F3577B34DA6A3CE929D0E0E4736-00f067aa0ba902b7-01"))
                .isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void generateTraceId_returns32Hex() {
        String id = TraceIdParser.generateTraceId();
        assertThat(id).hasSize(32).matches("[0-9a-f]{32}");
    }

    @Test
    void resolve_traceparentWithoutVersion_returnsNull() {
        assertThat(TraceIdParser.resolve("not-a-traceparent", "fallback")).hasSize(32);
    }
}