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
package com.richie.component.http.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpRequestSupportTest {

    @Test
    void buildUrlWithParamsHandlesFragmentAndExistingQuery() {
        String url = HttpRequestSupport.buildUrlWithParams(
                "https://example.com/api?existing=1#frag",
                Map.of("page", "2", "q", "a b"));

        assertThat(url).startsWith("https://example.com/api?existing=1&");
        assertThat(url).contains("page=2");
        assertThat(url).contains("q=a+b");
        assertThat(url).endsWith("#frag");
    }

    @Test
    void buildUrlWithParamsSkipsNullKeys() {
        Map<String, String> params = new java.util.HashMap<>();
        params.put(null, "x");
        params.put("ok", "1");

        assertThat(HttpRequestSupport.buildUrlWithParams("https://example.com", params))
                .isEqualTo("https://example.com?ok=1");
    }

    @Test
    void buildUrlWithParamsReturnsOriginalWhenEmpty() {
        assertThat(HttpRequestSupport.buildUrlWithParams("https://example.com", null))
                .isEqualTo("https://example.com");
        assertThat(HttpRequestSupport.buildUrlWithParams("https://example.com", Map.of()))
                .isEqualTo("https://example.com");
    }

    @Test
    void serializeBodySupportsStringAndObject() {
        assertThat(HttpRequestSupport.serializeBody("plain"))
                .isEqualTo("plain".getBytes(StandardCharsets.UTF_8));
        assertThat(HttpRequestSupport.serializeBody(Map.of("a", 1))).isNotEmpty();
        assertThat(HttpRequestSupport.serializeBody(null)).isNull();
    }

    @Test
    void executeWithTimeoutReturnsImmediatelyWhenDisabled() {
        String value = HttpRequestSupport.executeWithTimeout(null, () -> "ok");
        assertThat(value).isEqualTo("ok");
        assertThat(HttpRequestSupport.executeWithTimeout(Duration.ZERO, () -> "ok")).isEqualTo("ok");
    }

    @Test
    void executeWithTimeoutPropagatesRuntimeException() {
        assertThatThrownBy(() -> HttpRequestSupport.executeWithTimeout(
                Duration.ofSeconds(1),
                () -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");
    }
}
