package cn.richie696.component.vector.bulk;

import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.model.VectorRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 向量批量入库编排器。
 *
 * <p>该类仅编排"嵌入 → 攒批 → 持久化"三段流程，不依赖任何具体 provider 的服务类；provider 只需通过
 * 两个 SPI 回调（{@link RecordEmbedder} 完成按内容模态的嵌入、{@link EmbeddedRecordWriter} 完成已嵌入
 * 记录的批量写入）接入即可。模型与向量库实现保持解耦，使并发、背压和事件协议可独立测试。</p>
 *
 * <p>之所以走 SPI 而不是继承，是因为向量组件同时覆盖 7 个 provider（Milvus/Qdrant/Redis/PG/Mongo/Neo4j/
 * Weaviate）和多种嵌入模型（自管嵌入 / 库管嵌入）；把可变部分压成两个函数接口后，编排本身就可以
 * 走 RAG 流程中相同的反应式流，库管嵌入（{@link StoreManagedRecordWriter}）成为另一个分支。同一份事件
 * 协议 {@link BulkOperationEvent} 既能被业务埋点消费，也能被知识库应用做幂等去重和重试定位。</p>
 *
 * <p>调用关系：{@code VectorService.upsertAll(...)} 走 {@link cn.richie696.component.vector.service.impl.AbstractVectorService}
 * 进入本类，本类产出 {@link Flux}&lt;{@link BulkOperationEvent}&gt; 流给上层（{@code VectorProjectionWriter}、
 * 知识库门面、外部看板）；同时它对 {@link cn.richie696.component.vector.config.VectorProperties.Bulk} 配置
 * 进行参数规整（必须为正数）。</p>
 *
 * <p>失败语义：单条记录的嵌入或落库异常被局部 {@code onErrorResume} 捕获并转化为 {@link BulkOperationEvent.ItemFailed}，
 * 不会中断整批；最终无论成败都会通过 {@link BulkOperationEvent.Completed} 发出一份终态
 * {@link BulkOperationSummary}，让消费者能稳定拿到 batch 级清理依据。事件不暴露原始异常对象，仅透出
 * 异常类名和被截断到 512 字符的消息，便于跨线程/跨进程持久化和转发。</p>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public final class BulkIngestionPipeline {

    /**
     * 嵌入回调 SPI。
     *
     * <p>由 provider 适配器实现：按 {@link VectorRecord} 的内容模态调用对应的
     * {@code EmbeddingModel}，返回稠密向量；嵌入阶段（{@link BulkProcessingStage#EMBEDDING}）的每一次
     * 成功调用会计入 {@link BulkOperationSummary#embeddingRequests()}。该接口不感知索引或持久化细节，
     * 可在任意线程上阻塞执行（本编排器会自动调度到 {@code boundedElastic}）。</p>
     */
    @FunctionalInterface
    public interface RecordEmbedder {
        /**
         * 把单条记录嵌入为浮点向量。
         *
         * @param record 待嵌入的向量记录（已校验非空、indexName 与批量一致）
         * @return 嵌入向量，长度由模型决定
         */
        float[] embed(VectorRecord record);
    }

    /**
     * 持久化回调 SPI：把已嵌入的记录一次性写入底层向量库。
     *
     * <p>每次成功调用会计入 {@link BulkOperationSummary#writeRequests()}。本编排器会在调用前预攒
     * {@code writeBatchSize} 大小或经过 {@code writeFlushIntervalMs} 时间窗口的批次，以摊薄 provider
     * 的网络往返；写库阶段（{@link BulkProcessingStage#PERSISTING}）的失败会以"整批失败"语义回放为
     * 每个 item 一条 {@link BulkOperationEvent.ItemFailed}。</p>
     *
     * <p>实现要求：阻塞调用即可，编排器会负责将其调度到 {@code boundedElastic}；不应自行做拆批或并发，
     * 拆批策略统一由本类控制以保证事件协议一致性。</p>
     */
    @FunctionalInterface
    public interface EmbeddedRecordWriter {
        /**
         * 把一个嵌入批次写入目标索引。
         *
         * @param indexName 目标索引名
         * @param records   已嵌入的记录批次（不可变副本，调用方可安全持有）
         */
        void write(String indexName, List<EmbeddedVectorRecord> records);
    }

    /**
     * 库管嵌入写入回调：当底层 {@code VectorStore} 自身支持并完成嵌入时使用，避免重复调用 {@code EmbeddingModel}。
     *
     * <p>对应"嵌入由向量库负责"的场景（如部分 Spring AI 原生 store）。启用此回调后，本编排器跳过
     * {@link RecordEmbedder} 阶段，事件流上不再出现 {@link BulkProcessingStage#EMBEDDING}，仅保留
     * {@link BulkProcessingStage#PERSISTING}；用于显式区分"我们嵌"和"库嵌"两条管线。</p>
     */
    @FunctionalInterface
    public interface StoreManagedRecordWriter {
        /**
         * 把原始记录批次交给库管嵌入的向量库。
         *
         * @param indexName 目标索引名
         * @param records   待写入的原始记录批次
         */
        void write(String indexName, List<VectorRecord> records);
    }

    /**
     * 已完成文本嵌入、尚未持久化的内部数据传输对象。
     *
     * <p>这是嵌入阶段（{@link RecordEmbedder}）与持久化阶段（{@link EmbeddedRecordWriter}）之间的
     * 不可变数据载体；embedding 数组在构造时被克隆，访问器也返回克隆副本，从而避免下游写库线程
     * 与嵌入线程间的内部状态泄漏。</p>
     *
     * @param itemId    批量事件追踪的 itemId，用于在 {@link BulkOperationEvent} 的多条子事件之间
     *                  关联单条记录。
     * @param record    原始 {@link VectorRecord}，包含待入库的完整业务字段。
     * @param embedding 已计算好的稠密向量；构造时与读取时都会被克隆，避免线程间共享底层缓冲区。
     * @author richie696
     * @version 1.0
     * @since 2025-07-01
     */
    public record EmbeddedVectorRecord(
            String itemId,
            VectorRecord record,
            float[] embedding) {
        public EmbeddedVectorRecord {
            if (itemId == null || itemId.isBlank() || record == null || embedding == null || embedding.length == 0) {
                throw new IllegalArgumentException("已嵌入记录的 itemId、record 和 embedding 不能为空");
            }
            embedding = embedding.clone();
        }

        /**
         * 返回一份深拷贝的 embedding 数组，保证上游嵌入阶段不会因为下游写库阶段的副作用而污染
         * 共享的浮点缓冲区。
         *
         * @return 克隆后的 embedding 数组
         */
        @Override
        public float[] embedding () {
            return embedding.clone();
        }
    }

    private final RecordEmbedder embedder;
    private final EmbeddedRecordWriter writer;
    private final StoreManagedRecordWriter storeManagedWriter;

    /**
     * 构造"自管嵌入"管线。
     *
     * <p>由 provider 通过 {@link RecordEmbedder} 完成嵌入、本类负责拆批后用
     * {@link EmbeddedRecordWriter} 落库；这是最常见的路径。库管嵌入回调为 {@code null}。</p>
     *
     * @param embedder 嵌入回调 SPI
     * @param writer   持久化回调 SPI
     */
    public BulkIngestionPipeline(RecordEmbedder embedder, EmbeddedRecordWriter writer) {
        this.embedder = embedder;
        this.writer = writer;
        this.storeManagedWriter = null;
    }

    /**
     * 构造"库管嵌入"管线。
     *
     * <p>嵌入由底层 {@code VectorStore} 在写入时完成，事件流上不再出现 {@link BulkProcessingStage#EMBEDDING}
     * 阶段；用于减少一次 EmbeddingModel 调用，但要求 provider 真的支持库内嵌入。</p>
     *
     * @param writer 库管嵌入写入回调 SPI
     */
    public BulkIngestionPipeline(StoreManagedRecordWriter writer) {
        this.embedder = null;
        this.writer = null;
        this.storeManagedWriter = writer;
    }

    /**
     * 启动一次批量入库反应式流。
     *
     * <p>流的开头是一条 {@link BulkOperationEvent.Started}，中间穿插若干
     * {@link BulkOperationEvent.ItemStarted} / {@link BulkOperationEvent.ItemSucceeded} /
     * {@link BulkOperationEvent.ItemFailed}，最后无论成败都发出一条
     * {@link BulkOperationEvent.Completed}（带终态 {@link BulkOperationSummary}）。</p>
     *
     * <p>所有调度参数（{@code embeddingConcurrency}、{@code writeBatchSize}、{@code writeConcurrency}、
     * {@code writeFlushIntervalMs}）必须为正数；调用方传入 {@code null} 时使用默认
     * {@link VectorProperties.Bulk}。流的每个 item 由 {@link VectorRecord#itemId()} 提供追踪键，
     * 没有时记为 {@code "unknown"}，便于上层埋点聚合。</p>
     *
     * @param indexName 目标索引名（必填）
     * @param records   待写入的向量记录反应式源（{@code null} 将立即返回错误流）
     * @param options   调度参数；为 {@code null} 时使用默认配置
     * @return 批量事件反应式流
     * @throws IllegalArgumentException 当 {@code indexName} 为空或任一调度参数非正时
     */
    public Flux<BulkOperationEvent> execute(String indexName, Flux<VectorRecord> records,
                                            VectorProperties.Bulk options) {
        requireIndexName(indexName);
        if (records == null) {
            return Flux.error(new IllegalArgumentException("records 不能为空"));
        }
        VectorProperties.Bulk effectiveOptions = options == null ? new VectorProperties.Bulk() : options;
        String operationId = UUID.randomUUID().toString();
        Instant startedAt = Instant.now();
        Counters counters = new Counters();

        int embeddingConcurrency = positive(effectiveOptions.getEmbeddingConcurrency(), "embeddingConcurrency");
        int writeBatchSize = positive(effectiveOptions.getWriteBatchSize(), "writeBatchSize");
        int writeConcurrency = positive(effectiveOptions.getWriteConcurrency(), "writeConcurrency");
        long writeFlushIntervalMs = positive(effectiveOptions.getWriteFlushIntervalMs(), "writeFlushIntervalMs");

        if (storeManagedWriter != null) {
            return executeStoreManaged(indexName, records, operationId, startedAt, counters,
                    writeBatchSize, writeConcurrency, writeFlushIntervalMs);
        }

        Flux<Signal> signals = records.flatMap(record -> embed(operationId, indexName, record, counters),
                embeddingConcurrency);

        Flux<BulkOperationEvent> work = signals.publish(shared -> Flux.merge(
                shared.ofType(EventSignal.class).map(EventSignal::event),
                shared.ofType(EmbeddedSignal.class)
                        .map(EmbeddedSignal::record)
                        .bufferTimeout(writeBatchSize, Duration.ofMillis(writeFlushIntervalMs))
                        .filter(chunk -> !chunk.isEmpty())
                        .flatMap(chunk -> persist(operationId, indexName, chunk, counters),
                                writeConcurrency)
        ));

        return Flux.concat(
                        Mono.just(new BulkOperationEvent.Started(operationId, BulkOperationType.UPSERT, startedAt)),
                        work.onErrorResume(error -> Mono.just(failed(operationId, "unknown",
                                BulkProcessingStage.EMBEDDING, error, counters))),
                        Mono.fromSupplier(() -> completed(operationId, BulkOperationType.UPSERT, startedAt, counters))
                )
                .cast(BulkOperationEvent.class);
    }

    private Flux<BulkOperationEvent> executeStoreManaged(String indexName, Flux<VectorRecord> records,
                                                         String operationId, Instant startedAt, Counters counters,
                                                         int writeBatchSize, int writeConcurrency,
                                                         long writeFlushIntervalMs) {
        Flux<Signal> signals = records.map(record -> {
            try {
                return new PreparedSignal(new PreparedVectorRecord(prepare(record, indexName), record));
            } catch (Exception error) {
                return new EventSignal(failed(operationId, "unknown", BulkProcessingStage.PERSISTING, error, counters));
            }
        });
        Flux<BulkOperationEvent> work = signals.publish(shared -> Flux.merge(
                shared.ofType(EventSignal.class).map(EventSignal::event),
                shared.ofType(PreparedSignal.class).map(PreparedSignal::record)
                        .bufferTimeout(writeBatchSize, Duration.ofMillis(writeFlushIntervalMs))
                        .filter(chunk -> !chunk.isEmpty())
                        .flatMap(chunk -> persistStoreManaged(operationId, indexName, chunk, counters), writeConcurrency)
        ));
        return Flux.concat(Mono.just(new BulkOperationEvent.Started(operationId, BulkOperationType.UPSERT, startedAt)),
                        work, Mono.fromSupplier(() -> completed(operationId, BulkOperationType.UPSERT, startedAt, counters)))
                .cast(BulkOperationEvent.class);
    }

    private Flux<BulkOperationEvent> persistStoreManaged(String operationId, String indexName,
                                                         List<PreparedVectorRecord> chunk, Counters counters) {
        List<BulkOperationEvent> started = chunk.stream().map(item -> new BulkOperationEvent.ItemStarted(
                        operationId, item.itemId(), BulkProcessingStage.PERSISTING, Instant.now()))
                .map(BulkOperationEvent.class::cast).toList();
        return Flux.concat(Flux.fromIterable(started), Mono.fromRunnable(() -> {
                    counters.writeRequests.incrementAndGet();
                    storeManagedWriter.write(indexName, chunk.stream().map(PreparedVectorRecord::record).toList());
                }).thenMany(Flux.fromIterable(chunk).map(item -> succeeded(operationId,
                                new EmbeddedVectorRecord(item.itemId(), item.record(), new float[]{0.0f}), counters))
                        .cast(BulkOperationEvent.class))
                .onErrorResume(error -> Flux.fromIterable(chunk).map(item -> failed(operationId, item.itemId(),
                        BulkProcessingStage.PERSISTING, error, counters))));
    }

    private Flux<Signal> embed(String operationId, String indexName, VectorRecord record, Counters counters) {
        String itemId;
        try {
            itemId = prepare(record, indexName);
        } catch (Exception error) {
            return Flux.just(new EventSignal(failed(operationId, "unknown",
                    BulkProcessingStage.EMBEDDING, error, counters)));
        }
        return Flux.concat(
                Mono.<Signal>just(new EventSignal(new BulkOperationEvent.ItemStarted(
                        operationId, itemId, BulkProcessingStage.EMBEDDING, Instant.now()))),
                Mono.fromCallable(() -> {
                            counters.embeddingRequests.incrementAndGet();
                            return new EmbeddedVectorRecord(itemId, record, embedder.embed(record));
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .<Signal>map(EmbeddedSignal::new)
                        .onErrorResume(error -> Mono.just(new EventSignal(failed(
                                operationId, itemId, BulkProcessingStage.EMBEDDING, error, counters))))
        );
    }

    private Flux<BulkOperationEvent> persist(String operationId, String indexName,
                                             List<EmbeddedVectorRecord> chunk, Counters counters) {
        List<BulkOperationEvent> started = chunk.stream()
                .map(item -> new BulkOperationEvent.ItemStarted(operationId, item.itemId(),
                        BulkProcessingStage.PERSISTING, Instant.now()))
                .map(BulkOperationEvent.class::cast)
                .toList();

        return Flux.concat(Flux.fromIterable(started), Mono.fromCallable(() -> {
                    counters.writeRequests.incrementAndGet();
                    writer.write(indexName, List.copyOf(chunk));
                    return chunk;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(written -> Flux.fromIterable(written)
                        .<BulkOperationEvent>map(item -> succeeded(operationId, item, counters)))
                .onErrorResume(error -> Flux.fromIterable(chunk)
                        .map(item -> failed(operationId, item.itemId(),
                                BulkProcessingStage.PERSISTING, error, counters))));
    }

    private BulkOperationEvent.ItemSucceeded succeeded(String operationId, EmbeddedVectorRecord item,
                                                       Counters counters) {
        counters.succeeded.incrementAndGet();
        return new BulkOperationEvent.ItemSucceeded(operationId, item.itemId(), item.record().getId(), Instant.now());
    }

    private BulkOperationEvent.ItemFailed failed(String operationId, String itemId,
                                                 BulkProcessingStage stage, Throwable error, Counters counters) {
        counters.failed.incrementAndGet();
        return new BulkOperationEvent.ItemFailed(operationId, itemId, stage,
                error.getClass().getSimpleName(), safeMessage(error), Instant.now());
    }

    private BulkOperationEvent.Completed completed(String operationId, BulkOperationType type,
                                                   Instant startedAt, Counters counters) {
        return new BulkOperationEvent.Completed(operationId, type,
                new BulkOperationSummary(counters.succeeded.get(), counters.failed.get(),
                        Duration.between(startedAt, Instant.now()), counters.embeddingRequests.get(),
                        counters.writeRequests.get()), Instant.now());
    }

    private static void requireIndexName(String indexName) {
        if (indexName == null || indexName.isBlank()) {
            throw new IllegalArgumentException("indexName 不能为空");
        }
    }

    private static String prepare(VectorRecord record, String indexName) {
        if (record == null || record.getContent() == null) {
            throw new IllegalArgumentException("VectorRecord 及其 content 不能为空");
        }
        if (record.getIndexName() == null || !record.getIndexName().equals(indexName)) {
            throw new IllegalArgumentException("VectorRecord.indexName 必须与批量操作 indexName 一致");
        }
        if (record.getId() == null || record.getId().isBlank()) {
            record.setId(UUID.randomUUID().toString());
        }
        return itemId(record);
    }

    private static String itemId(VectorRecord record) {
        return record == null || record.itemId() == null || record.itemId().isBlank()
                ? "unknown" : record.itemId();
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 512 ? message : message.substring(0, 512);
    }

    private static int positive(int value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " 必须大于 0");
        }
        return value;
    }

    private static long positive(long value, String property) {
        if (value <= 0) {
            throw new IllegalArgumentException(property + " 必须大于 0");
        }
        return value;
    }

    private sealed

    interface Signal permits EventSignal, EmbeddedSignal, PreparedSignal {
    }

    private record EventSignal(BulkOperationEvent event) implements

    Signal {
    }

    private record EmbeddedSignal(EmbeddedVectorRecord record) implements

    Signal {
    }

    private record PreparedSignal(PreparedVectorRecord record) implements

    Signal {
    }

    private record PreparedVectorRecord(String itemId, VectorRecord record) {
    }

    private static final class Counters {
        private final AtomicLong succeeded = new AtomicLong();
        private final AtomicLong failed = new AtomicLong();
        private final AtomicLong embeddingRequests = new AtomicLong();
        private final AtomicLong writeRequests = new AtomicLong();
    }
}
