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
