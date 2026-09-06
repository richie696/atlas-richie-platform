/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import cn.richie696.component.vector.diagnostics.VectorErrorCategory;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.topology.VectorStoreId;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerVectorStoreObservationHookTest {

    @Test
    void exportsBoundedMetricsAndSanitizedTraceAttributes() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        ObservationRegistry observations = ObservationRegistry.create();
        List<Observation.Context> stopped = new ArrayList<>();
        observations.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context context) {
                stopped.add(context);
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        MicrometerVectorStoreObservationHook hook =
                new MicrometerVectorStoreObservationHook(meters, observations);
        VectorStoreObservationEvent event = new VectorStoreObservationEvent(
                VectorStoreId.of("synthetic-store"),
                VectorProvider.MILVUS,
                VectorStoreOperation.SEARCH_TEXT,
                VectorStoreOperationResult.SUCCESS,
                Duration.ofMillis(12),
                VectorStoreObservationEvent.fingerprint("private-physical-index"),
                Set.of("NATIVE_FILTER", "SCORE_STAGES"),
                VectorErrorCategory.NONE);

        hook.onEvent(event);

        assertThat(meters.find(MicrometerVectorStoreObservationHook.OPERATION_METRIC)
                .tags("store", "synthetic-store", "provider", "MILVUS",
                        "operation", "SEARCH_TEXT", "result", "SUCCESS")
                .timer())
                .satisfies(timer -> {
                    assertThat(timer).isNotNull();
                    assertThat(timer.count()).isEqualTo(1);
                    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(12.0D);
                });
        assertThat(stopped).singleElement().satisfies(context -> {
            assertThat(context.getLowCardinalityKeyValue("vector.store").getValue())
                    .isEqualTo("synthetic-store");
            assertThat(context.getHighCardinalityKeyValue("vector.index.fingerprint").getValue())
                    .isEqualTo(event.indexFingerprint());
            assertThat(context.getHighCardinalityKeyValue("vector.capabilities").getValue())
                    .isEqualTo("NATIVE_FILTER,SCORE_STAGES");
            assertThat(context.toString())
                    .doesNotContain("private-physical-index")
                    .doesNotContain("query")
                    .doesNotContain("principal");
        });
    }
}
