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
package com.richie.component.vector.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BatchEventTest {

    @Test
    void batchStarted_exposesBatchIdTotalAndTimestamp() {
        Instant now = Instant.now();
        BatchEvent.BatchStarted started = new BatchEvent.BatchStarted("b1", 5L, now);

        assertThat(started.batchId()).isEqualTo("b1");
        assertThat(started.timestamp()).isEqualTo(now);
        assertThat(started.total()).isEqualTo(5L);
    }

    @Test
    void batchCompleted_exposesStats() {
        Instant now = Instant.now();
        BatchStats stats = new BatchStats(3, 3, 0, Duration.ofMillis(100), 3, 1);
        BatchEvent.BatchCompleted done = new BatchEvent.BatchCompleted("b1", stats, now);

        assertThat(done.batchId()).isEqualTo("b1");
        assertThat(done.stats()).isSameAs(stats);
        assertThat(done.timestamp()).isEqualTo(now);
    }

    @Test
    void itemStarted_exposesAllFields() {
        BatchEvent.ItemStarted s = new BatchEvent.ItemStarted(
                "b1", "item-1", Modality.TEXT, Stage.EMBEDDING, Instant.now());

        assertThat(s.itemId()).isEqualTo("item-1");
        assertThat(s.modality()).isEqualTo(Modality.TEXT);
        assertThat(s.stage()).isEqualTo(Stage.EMBEDDING);
    }

    @Test
    void stageChanged_exposesFromAndTo() {
        BatchEvent.StageChanged ch = new BatchEvent.StageChanged(
                "b1", "item-1", Modality.IMAGE, Stage.LOADED, Stage.EMBEDDING, Instant.now());

        assertThat(ch.fromStage()).isEqualTo(Stage.LOADED);
        assertThat(ch.toStage()).isEqualTo(Stage.EMBEDDING);
    }

    @Test
    void itemCompleted_exposesRecordId() {
        BatchEvent.ItemCompleted c = new BatchEvent.ItemCompleted(
                "b1", "item-1", "vec-001", Modality.TEXT, Instant.now());

        assertThat(c.itemId()).isEqualTo("item-1");
        assertThat(c.recordId()).isEqualTo("vec-001");
    }

    @Test
    void itemFailed_exposesStageAndError() {
        RuntimeException err = new RuntimeException("boom");
        BatchEvent.ItemFailed f = new BatchEvent.ItemFailed(
                "b1", "item-1", Stage.EMBEDDING, err, Instant.now());

        assertThat(f.failedStage()).isEqualTo(Stage.EMBEDDING);
        assertThat(f.error()).isSameAs(err);
    }
}