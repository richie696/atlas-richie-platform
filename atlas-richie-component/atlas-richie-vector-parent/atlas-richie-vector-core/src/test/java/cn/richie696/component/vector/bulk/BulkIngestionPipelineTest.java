package cn.richie696.component.vector.bulk;

import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.model.VectorRecord;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class BulkIngestionPipelineTest {

    @Test
    void execute_emitsLifecycleAndPersistsEveryTextRecord() {
        List<BulkIngestionPipeline.EmbeddedVectorRecord> persisted = new CopyOnWriteArrayList<>();
        BulkIngestionPipeline pipeline = new BulkIngestionPipeline(
                record -> new float[]{0.1f, 0.2f},
                (indexName, records) -> persisted.addAll(records));

        VectorProperties.Bulk options = new VectorProperties.Bulk()
                .setEmbeddingConcurrency(1)
                .setWriteConcurrency(1)
                .setWriteBatchSize(2)
                .setWriteFlushIntervalMs(1);

        List<BulkOperationEvent> events = pipeline.execute("knowledge", Flux.just(
                        VectorRecord.text("knowledge", "first").setId("v1"),
                        VectorRecord.text("knowledge", "second").setId("v2")), options)
                .collectList()
                .block();

        assertThat(events).isNotNull();
        assertThat(events.getFirst()).isInstanceOf(BulkOperationEvent.Started.class);
        assertThat(events.getLast()).isInstanceOf(BulkOperationEvent.Completed.class);
        assertThat(events.stream().filter(BulkOperationEvent.ItemSucceeded.class::isInstance)).hasSize(2);
        assertThat(persisted).extracting(item -> item.record().getId()).containsExactlyInAnyOrder("v1", "v2");

        BulkOperationEvent.Completed completed = (BulkOperationEvent.Completed) events.getLast();
        assertThat(completed.summary().succeeded()).isEqualTo(2);
        assertThat(completed.summary().failed()).isZero();
    }

    @Test
    void execute_convertsEmbeddingFailureToSafeItemEventAndCompletes() {
        BulkIngestionPipeline pipeline = new BulkIngestionPipeline(
                record -> {
                    throw new IllegalStateException("embedding unavailable");
                },
                (indexName, records) -> {
                });

        List<BulkOperationEvent> events = pipeline.execute("knowledge",
                        Flux.just(VectorRecord.text("knowledge", "content").setId("v1")),
                        new VectorProperties.Bulk())
                .collectList()
                .block();

        assertThat(events).anyMatch(BulkOperationEvent.ItemFailed.class::isInstance);
        BulkOperationEvent.ItemFailed failed = (BulkOperationEvent.ItemFailed) events.stream()
                .filter(BulkOperationEvent.ItemFailed.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(failed.errorCode()).isEqualTo("IllegalStateException");
        assertThat(failed.message()).isEqualTo("embedding unavailable");
        assertThat(events.getLast()).isInstanceOf(BulkOperationEvent.Completed.class);
    }
}
