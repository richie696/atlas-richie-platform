/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalObservationTest {

    @Test
    void collectorKeepsMeasuredAndAppliedStageEvents() {
        RetrievalObservationContext context = RetrievalObservationContext.forKnowledgeSearch(
                "kb-1", 50, false, true, true);
        RetrievalObservationCollector collector = new RetrievalObservationCollector(context);

        collector.onEvent(RetrievalObservationEvent.success(context, RetrievalStage.EMBEDDING,
                Duration.ofMillis(12), 1, 1536, true));
        collector.onEvent(RetrievalObservationEvent.success(context, RetrievalStage.RERANK,
                Duration.ofMillis(8), 50, 10, true));
        collector.onEvent(RetrievalObservationEvent.skipped(context, RetrievalStage.DIVERSIFY, 10, 10));

        RetrievalObservationSnapshot snapshot = collector.snapshot();
        assertThat(snapshot.measuredElapsed(RetrievalStage.EMBEDDING)).isEqualTo(Duration.ofMillis(12));
        assertThat(snapshot.measuredElapsed(RetrievalStage.RERANK)).isEqualTo(Duration.ofMillis(8));
        assertThat(snapshot.applied(RetrievalStage.RERANK)).isTrue();
        assertThat(snapshot.measuredElapsed(RetrievalStage.DIVERSIFY)).isNull();
    }

    @Test
    void collectorIgnoresEventsFromAnotherOperation() {
        RetrievalObservationContext context = RetrievalObservationContext.forTextSearch("kb-1", 10, false);
        RetrievalObservationContext other = RetrievalObservationContext.forTextSearch("kb-1", 10, false);
        RetrievalObservationCollector collector = new RetrievalObservationCollector(context);

        collector.onEvent(RetrievalObservationEvent.success(other, RetrievalStage.VECTOR_SEARCH,
                Duration.ofMillis(4), 10, 2, true));

        assertThat(collector.snapshot().stages()).isEmpty();
    }
}
