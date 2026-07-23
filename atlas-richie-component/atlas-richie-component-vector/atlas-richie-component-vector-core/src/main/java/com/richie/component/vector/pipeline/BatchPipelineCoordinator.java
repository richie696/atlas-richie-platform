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
package com.richie.component.vector.pipeline;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.BatchEvent;
import com.richie.component.vector.service.impl.AbstractVectorService;
import com.richie.component.vector.model.BatchStats;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.Stage;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 批量管线协调器 — 把 {@link AbstractVectorService} 中批量嵌入/写入的事件流管线抽离到独立类。
 * <p>
 * <b>职责单一</b>：本类只负责把 {@code Flux<VectorRecord>} 按 §6 配置编排成
 * {@code Flux<BatchEvent>}（Stage A 嵌入 + 去重 + Stage B 攒批写入 + 终态统计）。
 * <p>
 * <b>依赖注入</b>：通过三个回调接口解耦 {@code AbstractVectorService}：
 * <ul>
 *   <li>{@link ConfigProvider}：每批运行时取最新 {@link VectorProperties.Batch}（保证 setter 注入后立即生效）</li>
 *   <li>{@link Embedder}：嵌入模型抽象（生产环境委托 {@code EmbeddingModel.embed()} / 多模态服务）</li>
 *   <li>{@link DocumentWriter}：批量写入抽象（生产环境委托 {@code AbstractVectorService.addEmbeddings}）</li>
 * </ul>
 * <p>
 * <b>三阶段架构</b>：
 * <ul>
 *   <li><b>Stage A (embed producer)</b>：{@code records.flatMap(..., embeddingConcurrency)}，
 *       每条记录串行嵌入（每次嵌入仍是一次 EmbeddingModel.embed 调用）→ 发 ItemStarted(LOADED) +
 *       StageChanged(LOADED→EMBEDDED) → 推 EmbeddedItem 到 Sinks.Many</li>
 *   <li><b>Stage B (write consumer)</b>：{@code sink.asFlux().bufferTimeout(writeBatchSize, 50ms)}
 *       按条数 / 时间窗口攒批 → {@code flatMap(chunk -> processChunk(...), writeConcurrency)}
 *       一次 {@code addEmbeddings(List<Document>)} 写入整 chunk，发 ItemStarted(PERSISTING) +
 *       StageChanged(PERSISTING→PERSISTED) + ItemCompleted</li>
 *   <li><b>Stage C (terminal)</b>：BatchCompleted tail</li>
 * </ul>
 * <p>
 * <b>§6.1 行为控制</b>：
 * <ul>
 *   <li>{@code embeddingConcurrency}：Stage A 的并发度</li>
 *   <li>{@code writeBatchSize}：bufferTimeout 上限，也是单次 addEmbeddings 的 doc 数</li>
 *   <li>{@code writeConcurrency}：Stage B 同时处理的 chunk 数</li>
 *   <li>{@code backpressureBuffer}：Sinks.Many.onBackpressureBuffer 上限；嵌入阶段满后阻塞 1ms 后重试</li>
 *   <li>{@code failFast}：true 时首个失败立即抛错，Stage A/B 任一阶段都会终止整个批次</li>
 *   <li>{@code dedupCacheSize}：大于 0 时按 SHA-256 跳过重复（仅发 ItemCompleted，不推 Sinks）</li>
 *   <li>{@code itemIdSource}：决定 itemId 取值（METADATA / ID / HASH）</li>
 * </ul>
 * <p>
 * <b>事件顺序</b>：每条记录 5 个事件，跨 Stage A/B 拼接：LOADED + EMBEDDED 由 Stage A 发；
 * PERSISTING + PERSISTED + ItemCompleted 由 Stage B 按 chunk 内顺序发。同一记录 5 个事件保持
 * "先 A 后 B" 的整体顺序；跨记录可能交错（与 flatMap 行为一致）。
 *
 * @author richie696
 * @since 2.0.0
 */
public class BatchPipelineCoordinator {

    /**
     * 默认批量配置 — 当 {@link ConfigProvider} 返回 {@code null} 时使用。
     * <p>
     * 字段值与 §6.1 配置表的默认值一致，确保无配置场景行为不变（串行嵌入 + 串行写入 + 失败继续）。
     */
    public static final VectorProperties.Batch DEFAULT_BATCH = new VectorProperties.Batch();

    /**
     * 配置回调 — 每批运行时调用 {@link #get()} 取当前 {@link VectorProperties.Batch}。
     * <p>
     * 抽象成回调的目的：{@code AbstractVectorService.vectorProperties} 通过 setter 注入
     * （{@code @Autowired(required = false)}），构造器调用时可能为 null；每批重新读取保证 setter
     * 注入后下次 {@link #run} 立即生效。
     */
    @FunctionalInterface
    public interface ConfigProvider {
        VectorProperties.Batch get();
    }

    /**
     * 嵌入模型抽象 — production 实现委托 {@code AbstractVectorService.embedForBatch}。
     */
    @FunctionalInterface
    public interface Embedder {
        float[] embed(VectorRecord record);
    }

    /**
     * 批量写入抽象 — production 实现委托 {@code AbstractVectorService.addEmbeddings}。
     */
    @FunctionalInterface
    public interface DocumentWriter {
        void write(String indexName, List<Document> docs);
    }

    private final ConfigProvider config;
    private final Embedder embedder;
    private final DocumentWriter writer;

    public BatchPipelineCoordinator(ConfigProvider config, Embedder embedder, DocumentWriter writer) {
        this.config = config;
        this.embedder = embedder;
        this.writer = writer;
    }

    /**
     * 批量管线核心入口（Step 2 双阶段）：Sinks.Many 流式背压缓冲。
     * <p>
     * 每次调用都会从 {@link ConfigProvider} 重新读取 {@link VectorProperties.Batch}，
     * 因此 setter 注入新配置后下次 {@code run()} 自动按新配置工作。
     * <p>
     * 当前版本仅支持 ADD 语义；{@link AbstractVectorService#updateBatch} 通过相同管线复用
     * （delete+insert 等价语义由 {@code AbstractVectorService.update(VectorRecord)} 单独承担）。
     */
    public Flux<BatchEvent> run(String indexName, Flux<VectorRecord> records) {
        VectorProperties.Batch cfg = config.get() != null ? config.get() : DEFAULT_BATCH;
        String batchId = UUID.randomUUID().toString();
        Instant started = Instant.now();
        AtomicLong succeeded = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        AtomicLong embeddingApiCalls = new AtomicLong();
        AtomicLong writeApiCalls = new AtomicLong();

        Set<String> dedupSeen = cfg.getDedupCacheSize() > 0
                ? Collections.newSetFromMap(new ConcurrentHashMap<>())
                : null;
        int embedConcurrency = Math.max(1, cfg.getEmbeddingConcurrency());
        int writeBatchSize = Math.max(1, cfg.getWriteBatchSize());
        int writeConcurrency = Math.max(1, cfg.getWriteConcurrency());
        int backpressureBuffer = Math.max(1, cfg.getBackpressureBuffer());
        boolean failFast = cfg.isFailFast();

        Sinks.Many<EmbeddedItem> sink = Sinks.many()
                .unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(backpressureBuffer));

        Flux<BatchEvent> stageA = records.flatMap(
                        record -> processItemEmbed(batchId, record, cfg, embeddingApiCalls,
                                dedupSeen, sink, failFast),
                        embedConcurrency)
                // Stage A 终止（正常 / 异常 / 取消）时关闭 Sinks，Stage B 才能 drain 剩余并完成
                .doOnTerminate(sink::tryEmitComplete);

        Flux<BatchEvent> stageB = sink.asFlux()
                .bufferTimeout(writeBatchSize, Duration.ofMillis(50))
                .flatMap(
                        chunk -> processChunk(batchId, indexName, chunk, writeApiCalls, failFast),
                        writeConcurrency);

        Flux<BatchEvent> merged = Flux.merge(stageA, stageB)
                .doOnNext(event -> {
                    if (event instanceof BatchEvent.ItemCompleted) {
                        succeeded.incrementAndGet();
                    } else if (event instanceof BatchEvent.ItemFailed) {
                        failed.incrementAndGet();
                    }
                });

        Flux<BatchEvent> tail = Flux.defer(() -> Flux.just(new BatchEvent.BatchCompleted(
                batchId,
                new BatchStats(succeeded.get() + failed.get(),
                        succeeded.get(), failed.get(),
                        Duration.between(started, Instant.now()),
                        embeddingApiCalls.get(), writeApiCalls.get()),
                Instant.now())));

        return Flux.concat(
                        Flux.just((BatchEvent) new BatchEvent.BatchStarted(batchId, -1L, started)),
                        merged.onErrorResume(err -> Flux.empty()),
                        tail
                )
                .onErrorResume(err -> Flux.defer(() -> Flux.just((BatchEvent) new BatchEvent.BatchCompleted(
                        batchId,
                        new BatchStats(succeeded.get() + failed.get(),
                                succeeded.get(), failed.get(),
                                Duration.between(started, Instant.now()),
                                embeddingApiCalls.get(), writeApiCalls.get()),
                        Instant.now()))));
    }

    /**
     * 处理单个 write chunk（Stage B 调用）：对一组已嵌入的记录做一次 {@code writer.write(...)} 写入。
     * <p>
     * 与重构前一致的事件发射顺序（每条记录 3 个事件，由本方法负责后半段）：
     * <ol>
     *   <li>{@link BatchEvent.ItemStarted}(PERSISTING)</li>
     *   <li>{@link BatchEvent.StageChanged}(PERSISTING → PERSISTED)</li>
     *   <li>{@link BatchEvent.ItemCompleted}</li>
     * </ol>
     * 前半段（ItemStarted(LOADED) + StageChanged(LOADED → EMBEDDED)）由 Stage A 的
     * {@link #processItemEmbed} 负责。
     * <p>
     * 失败处理：
     * <ul>
     *   <li>{@code writer.write} 失败且 {@code failFast=false}：本 chunk 内每条记录发 ItemFailed(PERSISTING)，返回继续下一个 chunk</li>
     *   <li>{@code writer.write} 失败且 {@code failFast=true}：抛 RuntimeException → 上游 Flux.create 转 sink.error → 终止批次</li>
     * </ul>
     * <p>
     * 空 chunk 直接返回空 Flux（bufferTimeout 在上游完成时可能发出一个空 buffer）。
     */
    private Flux<BatchEvent> processChunk(String batchId, String indexName, List<EmbeddedItem> chunk,
                                          AtomicLong writeApiCalls, boolean failFast) {
        if (chunk == null || chunk.isEmpty()) {
            return Flux.empty();
        }
        return Flux.<BatchEvent>create(sink -> {
            try {
                processChunkInternal(batchId, indexName, chunk, writeApiCalls, failFast, sink);
                sink.complete();
            } catch (Exception e) {
                sink.error(e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 单 chunk 写入阶段内部处理（Flux.create 回调）：
     * <ol>
     *   <li>为 chunk 内每条记录发 ItemStarted(PERSISTING)</li>
     *   <li>一次 {@code writer.write(List<Document>)} 写入整 chunk；{@code writeApiCalls} 自增 1</li>
     *   <li>为每条记录发 StageChanged(PERSISTING → PERSISTED) + ItemCompleted；失败时改发 ItemFailed</li>
     * </ol>
     * <p>
     * Document 构造委托 {@code AbstractVectorService.toAiDocument(...)}：本协调器不持有该方法，
     * 所以 Stage B 的写侧把 EmbeddedItem 还原为 VectorRecord + float[] 透传给 writer。
     * （注：当前生产 writer 实现 {@code (idx, docs) -> addEmbeddings(idx, docs)} 已包含 Document 装配逻辑，
     * 协调器内不再重复执行。）
     */
    private void processChunkInternal(String batchId, String indexName, List<EmbeddedItem> chunk,
                                      AtomicLong writeApiCalls, boolean failFast,
                                      FluxSink<BatchEvent> sink) {
        for (EmbeddedItem item : chunk) {
            sink.next(new BatchEvent.ItemStarted(
                    batchId, item.itemId(), item.modality(), Stage.PERSISTING, Instant.now()));
        }

        try {
            List<Document> docs = new ArrayList<>(chunk.size());
            for (EmbeddedItem item : chunk) {
                docs.add(toDocument(item));
            }
            writer.write(indexName, docs);
            writeApiCalls.incrementAndGet();

            for (EmbeddedItem item : chunk) {
                sink.next(new BatchEvent.StageChanged(
                        batchId, item.itemId(), item.modality(),
                        Stage.PERSISTING, Stage.PERSISTED, Instant.now()));
                sink.next(new BatchEvent.ItemCompleted(
                        batchId, item.itemId(), item.record().getId(),
                        item.modality(), Instant.now()));
            }
        } catch (Exception e) {
            if (failFast) {
                throw new RuntimeException(e);
            }
            for (EmbeddedItem item : chunk) {
                sink.next(new BatchEvent.ItemFailed(
                        batchId, item.itemId(), Stage.PERSISTING, e, Instant.now()));
            }
        }
    }

    /**
     * Stage A 的单记录处理：去重检查 → 嵌入 → 发 2 个事件 → 把 EmbeddedItem 推入写侧 Sinks。
     * <p>
     * 每条记录发射的事件：
     * <ul>
     *   <li><b>去重命中</b>：仅发 {@link BatchEvent.ItemCompleted}（不推 Sinks，跳过嵌入/写入）</li>
     *   <li><b>嵌入成功</b>：发 ItemStarted(LOADED) → StageChanged(LOADED → EMBEDDED)，并把 EmbeddedItem 推入 sink</li>
     *   <li><b>嵌入失败</b>（failFast=false）：发 ItemStarted(LOADED) + ItemFailed(EMBEDDING)</li>
     *   <li><b>嵌入失败</b>（failFast=true）：发 ItemStarted(LOADED) 后由 {@code onErrorResume} 抛错 → 终止整个批次</li>
     * </ul>
     * <p>
     * 推 Sinks 时遇 {@code FAIL_OVERFLOW}（背压缓冲满）会自动 {@code Thread.sleep(1)} 后重试；
     * 遇 {@code FAIL_TERMINATED}（sink 已被关闭，例如上游被取消）静默丢弃。
     */
    private Flux<BatchEvent> processItemEmbed(String batchId, VectorRecord record,
                                              VectorProperties.Batch cfg,
                                              AtomicLong embeddingApiCalls,
                                              Set<String> dedupSeen,
                                              Sinks.Many<EmbeddedItem> sink,
                                              boolean failFast) {
        Modality modality = record.getContent() != null ? record.getContent().modality() : Modality.TEXT;

        String contentHash = computeContentHash(record);
        if (dedupSeen != null && contentHash != null && !dedupSeen.add(contentHash)) {
            String dupItemId = resolveItemId(record, contentHash, cfg);
            return Flux.just(new BatchEvent.ItemCompleted(
                    batchId, dupItemId, record.getId(), modality, Instant.now()));
        }

        String itemId = resolveItemId(record, contentHash, cfg);
        Instant now = Instant.now();

        // ItemStarted(LOADED) 先发，嵌入失败时订阅方也能看到 LOADED 状态
        Flux<BatchEvent> started = Flux.just(
                new BatchEvent.ItemStarted(batchId, itemId, modality, Stage.LOADED, now));

        return Flux.concat(
                started,
                Mono.fromCallable(() -> embedder.embed(record))
                        .doOnSuccess(v -> embeddingApiCalls.incrementAndGet())
                        .doOnSuccess(v -> emitToSinkWithBackpressure(sink,
                                new EmbeddedItem(record, v, itemId, modality)))
                        .flatMapMany(v -> Flux.just(
                                new BatchEvent.StageChanged(
                                        batchId, itemId, modality,
                                        Stage.LOADED, Stage.EMBEDDED, Instant.now()))))
                .onErrorResume(e -> {
                    if (failFast) {
                        throw new RuntimeException(e);
                    }
                    return Flux.just(new BatchEvent.ItemFailed(
                            batchId, itemId, Stage.EMBEDDING, e, Instant.now()));
                });
    }

    /**
     * 线程安全地推 EmbeddedItem 到 Sinks。
     * <p>
     * 调用者使用，多线程并发推时返回 {@code FAIL_NON_SERIALIZED} 会导致 item 丢失；
     * {@code emitNext} 配合 {@link Sinks.EmitFailureHandler#busyLooping} 在背压缓冲满 / 序列化冲突
     * 等瞬态失败下自动 sleep 后重试，对 {@code FAIL_TERMINATED} / {@code FAIL_CANCELLED} 永久失败则直接返回。
     */
    private static void emitToSinkWithBackpressure(Sinks.Many<EmbeddedItem> sink, EmbeddedItem item) {
        sink.emitNext(item, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(1)));
    }

    /**
     * 嵌入成功后的 record 元组 — Stage A 嵌入后推入 Sinks，Stage B 消费并批量写入。
     */
    record EmbeddedItem(VectorRecord record, float[] embedding,
                        String itemId, Modality modality) {}

    /**
     * 按 {@link VectorProperties.Batch#getItemIdSource()} 选择 itemId 取值来源。
     * <ul>
     *   <li>{@code METADATA}：{@code record.itemId()}（即 metadata.__itemId，fallback 到 id）</li>
     *   <li>{@code ID}：{@code record.getId()}</li>
     *   <li>{@code HASH}：{@code contentHash}（以内容寻址）</li>
     * </ul>
     * 任何来源解析失败时回退到 {@code record.getId()}，保证非空。
     */
    private String resolveItemId(VectorRecord record, String contentHash, VectorProperties.Batch cfg) {
        VectorProperties.Batch.ItemIdSource src = cfg.getItemIdSource();
        if (src == null) {
            src = VectorProperties.Batch.ItemIdSource.METADATA;
        }
        return switch (src) {
            case ID -> record.getId() != null ? record.getId() : record.itemId();
            case HASH -> {
                if (contentHash != null) yield contentHash;
                yield record.getId() != null ? record.getId() : record.itemId();
            }
            default -> record.itemId();
        };
    }

    /**
     * 异常路径下安全地拿一个 itemId 用于 {@link BatchEvent.ItemFailed} — 优先按 cfg 配置解析，
     * 失败则回退到 {@code record.itemId()}（内置 metadata.__itemId → id 兜底）。
     */
    private String safeItemId(VectorRecord record, VectorProperties.Batch cfg) {
        String hash = computeContentHash(record);
        try {
            return resolveItemId(record, hash, cfg);
        } catch (Exception ex) {
            return record.itemId();
        }
    }

    /**
     * 计算内容 SHA-256 — 用于去重与 HASH 模式 itemId 来源。
     * <p>
     * <b>当前实现</b>：仅对 TEXT 内容取 text() 字节；IMAGE 内容返回 {@code null}（不参与去重/HASH）。
     * 返回 {@code null} 时调用方应视为「无可用哈希」。
     */
    private String computeContentHash(VectorRecord record) {
        VectorContent content = record.getContent();
        if (content == null) {
            return null;
        }
        String text;
        if (content instanceof VectorContent.TextContent t) {
            text = t.text();
        } else if (content instanceof VectorContent.ImageContent) {
            // IMAGE 内容本 phase 不做去重 — 调用方按 null 处理
            return null;
        } else {
            return null;
        }
        if (text == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 在 JDK 标准算法集中，永远存在 — 出现即视为环境异常
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /**
     * EmbeddedItem → Document — Stage B 写侧装配 Spring AI Document。
     * <p>
     * 逻辑从原 {@code AbstractVectorService.toAiDocument} 同步：text 取 VectorContent.TextContent，
     * metadata 全量合并 + tags/source/status/namespace/content/mimeType/modality/embedding。
     */
    private Document toDocument(EmbeddedItem item) {
        VectorRecord record = item.record();
        float[] embedding = item.embedding();
        String text = "";
        if (record.getContent() instanceof VectorContent.TextContent t) {
            text = t.text();
        }
        Document aiDoc = new Document(record.getId(), text,
                record.getMetadata() != null ? record.getMetadata() : java.util.Map.of());
        if (record.getMetadata() != null) aiDoc.getMetadata().putAll(record.getMetadata());
        if (record.getTags() != null) aiDoc.getMetadata().put("tags", String.join(",", record.getTags()));
        if (record.getSource() != null) aiDoc.getMetadata().put("source", record.getSource());
        if (record.getStatus() != null) aiDoc.getMetadata().put("status", record.getStatus());
        if (record.getNamespace() != null) aiDoc.getMetadata().put("namespace", record.getNamespace());
        if (record.getContent() instanceof VectorContent.TextContent(String text1, String mimeType)) {
            aiDoc.getMetadata().put("content", text1);
            aiDoc.getMetadata().put("mimeType", mimeType);
            aiDoc.getMetadata().put("modality", Modality.TEXT.name());
        } else if (record.getContent() instanceof VectorContent.ImageContent img) {
            aiDoc.getMetadata().put("mimeType", img.mimeType());
            aiDoc.getMetadata().put("modality", Modality.IMAGE.name());
        }
        aiDoc.getMetadata().put("embedding", embedding);
        return aiDoc;
    }
}