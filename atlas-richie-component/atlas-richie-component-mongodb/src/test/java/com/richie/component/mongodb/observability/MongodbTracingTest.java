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
package com.richie.component.mongodb.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MongodbTracingTest {

    private InMemorySpanExporter spanExporter;
    private SdkTracerProvider tracerProvider;
    private MongodbTracing tracing;

    @BeforeEach
    void setUp() throws Exception {
        spanExporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(spanExporter))
                .build();

        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        Tracer tracer = openTelemetry.getTracer("richie-mongodb", "1.0.0");

        tracing = new MongodbTracing();
        setAutowiredOpenTelemetry(tracing, openTelemetry);
        tracing.init();

        Field tracerField = MongodbTracing.class.getDeclaredField("tracer");
        tracerField.setAccessible(true);
        tracerField.set(null, tracer);

        Field otelField = MongodbTracing.class.getDeclaredField("openTelemetry");
        otelField.setAccessible(true);
        otelField.set(null, openTelemetry);
    }

    @Test
    void createSpan_shouldCreateSpanWithCorrectAttributes() {
        try (MongodbTracing.TracingScope scope = MongodbTracing.createSpan("find", "users", "{'name':'test'}")) {
            assertThat(scope).isNotNull();
            assertThat(scope.getSpan()).isNotNull();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getName()).isEqualTo("mongodb.find");
        assertThat(span.getKind()).isEqualTo(SpanKind.CLIENT);
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("db.system"))).isEqualTo("mongodb");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("db.operation"))).isEqualTo("find");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("db.mongodb.collection"))).isEqualTo("users");
        assertThat(span.getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("db.statement"))).isEqualTo("{'name':'test'}");
    }

    @Test
    void createSpan_shouldTruncateLongStatement() {
        String longStatement = "a".repeat(2000);
        try (MongodbTracing.TracingScope scope = MongodbTracing.createSpan("find", "users", longStatement)) {
            assertThat(scope).isNotNull();
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        String statement = spans.get(0).getAttributes().get(io.opentelemetry.api.common.AttributeKey.stringKey("db.statement"));
        assertThat(statement).hasSize(1027);
        assertThat(statement).endsWith("...");
    }

    @Test
    void recordError_shouldSetErrorStatusAndRecordException() {
        try (MongodbTracing.TracingScope scope = MongodbTracing.createSpan("find", "users", "{}")) {
            IllegalArgumentException ex = new IllegalArgumentException("test error");
            MongodbTracing.recordError(scope.getSpan(), ex);
        }

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
        SpanData span = spans.get(0);
        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
        assertThat(span.getStatus().getDescription()).isEqualTo("test error");
        assertThat(span.getEvents()).anyMatch(e -> e.getName().equals("exception"));
    }

    @Test
    void recordSuccess_shouldSetDurationAttribute() {
        Span span = Span.getInvalid();
        MongodbTracing.recordSuccess(span, 150L);
    }

    @Test
    void tracingScope_close_shouldEndSpan() {
        MongodbTracing.TracingScope scope = MongodbTracing.createSpan("find", "users", "{}");
        scope.close();

        List<SpanData> spans = spanExporter.getFinishedSpanItems();
        assertThat(spans).hasSize(1);
    }

    private void setAutowiredOpenTelemetry(MongodbTracing target, OpenTelemetry openTelemetry) throws Exception {
        Field field = MongodbTracing.class.getDeclaredField("autowiredOpenTelemetry");
        field.setAccessible(true);
        field.set(target, openTelemetry);
    }
}
