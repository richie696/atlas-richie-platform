/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Optional framework bridge to Micrometer metrics and Observation tracing.
 * Applications choose their exporter by configuring the standard registries;
 * the vector component never owns exporter endpoints or credentials.
 */
public final class MicrometerVectorStoreObservationHook implements VectorStoreObservationHook {

    public static final String OPERATION_METRIC = "atlas.vector.store.operation";

    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    public MicrometerVectorStoreObservationHook(
            MeterRegistry meterRegistry, ObservationRegistry observationRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.observationRegistry = Objects.requireNonNull(
                observationRegistry, "observationRegistry must not be null");
    }

    @Override
    public void onEvent(VectorStoreObservationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        List<Tag> tags = event.metricTags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> Tag.of(entry.getKey(), entry.getValue()))
                .toList();
        Timer.builder(OPERATION_METRIC)
                .description("Vector Store operation latency")
                .tags(tags)
                .register(meterRegistry)
                .record(event.elapsed());

        Observation observation = Observation.createNotStarted(OPERATION_METRIC, observationRegistry)
                .contextualName("vector " + event.operation().name().toLowerCase(java.util.Locale.ROOT));
        event.traceAttributes().forEach((key, value) -> {
            if (isLowCardinalityTraceAttribute(key)) {
                observation.lowCardinalityKeyValue(key, value);
            } else {
                observation.highCardinalityKeyValue(key, value);
            }
        });
        observation.highCardinalityKeyValue("vector.elapsed.nanos", Long.toString(event.elapsed().toNanos()));
        observation.start().stop();
    }

    private static boolean isLowCardinalityTraceAttribute(String key) {
        return switch (key) {
            case "vector.store", "vector.provider", "vector.operation", "vector.result",
                    "vector.error.category" -> true;
            default -> false;
        };
    }
}
