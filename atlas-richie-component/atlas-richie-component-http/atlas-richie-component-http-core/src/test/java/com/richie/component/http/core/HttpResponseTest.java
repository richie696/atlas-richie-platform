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
package com.richie.component.http.core;

import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpResponseTest {

    @Test
    void byteResponseExposesBodyHelpers() {
        HttpResponse response = HttpResponse.of(
                201,
                Map.of("X-Test", List.of("1")),
                "{\"name\":\"demo\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.isSuccessful()).isTrue();
        assertThat(response.headers()).containsKey("X-Test");
        assertThat(response.bodyAsString()).contains("demo");
        assertThat(response.bodyAs(Demo.class).name()).isEqualTo("demo");
        assertThat(response.bodyAs(new TypeReference<Map<String, String>>() {
        })).containsEntry("name", "demo");
    }

    @Test
    void streamResponseKeepsBodyNull() {
        HttpResponse response = HttpResponse.of(200, Map.of(), new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)));

        assertThat(response.body()).isNull();
        assertThat(response.bodyStream()).isNotNull();
        assertThat(response.bodyAsString()).isNull();
        assertThat(response.bodyAs(String.class)).isNull();
    }

    record Demo(String name) {
    }
}
