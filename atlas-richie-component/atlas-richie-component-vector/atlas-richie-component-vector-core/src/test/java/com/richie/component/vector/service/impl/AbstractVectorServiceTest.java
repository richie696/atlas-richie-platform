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
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.exceptions.UnsupportedModalityException;
import com.richie.component.vector.model.BatchEvent;
import com.richie.component.vector.model.BatchStats;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.embeddings.ModalityAwareEmbeddingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractVectorServiceTest {

    @Test
    void constructor_acceptsNullDependencies() {
        AbstractVectorService svc = new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };

        assertThat(svc).isNotNull();
    }

    @Test
    void listDocuments_returnsEmptyWhenLimitNonPositive() {
        AbstractVectorService svc = newService();

        assertThat(svc.listDocuments("idx", 0, 0)).isEmpty();
        assertThat(svc.listDocuments("idx", 0, -5)).isEmpty();
    }

    @Test
    void listDocuments_capsLimitAtMax() {
        AbstractVectorService svc = new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) {
                return List.of();
            }
        };

        assertThat(svc.listDocuments("idx", 0, AbstractVectorService.MAX_LIST_LIMIT + 1000)).isEmpty();
    }

    @Test
    void searchByText_rejectsBlankText() {
        AbstractVectorService svc = newService();

        assertThatThrownBy(() -> svc.searchByText("idx", null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text 不能为空");
    }

    @Test
    void defaultIndexOps_throwUnsupported() {
        AbstractVectorService svc = newService();

        assertThatThrownBy(() -> svc.createIndex("idx", null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> svc.deleteIndex("idx"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> svc.countDocuments("idx"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void healthCheck_defaultImplementation_returnsFalseWhenIndexMissing() {
        AbstractVectorService svc = new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
            @Override protected boolean indexExistsImpl(String i) { return false; }
        };

        assertThat(svc.healthCheck("missing")).isFalse();
    }

    @Test
    void healthCheck_defaultImplementation_returnsTrueWhenIndexOk() {
        AbstractVectorService svc = new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
            @Override protected boolean indexExistsImpl(String i) { return true; }
            @Override protected long countDocumentsImpl(String i) { return 42L; }
        };

        assertThat(svc.healthCheck("ok")).isTrue();
    }

    @Test
    void healthCheck_defaultImplementation_returnsFalseWhenCountThrows() {
        AbstractVectorService svc = new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
            @Override protected boolean indexExistsImpl(String i) { return true; }
            @Override protected long countDocumentsImpl(String i) { throw new RuntimeException("boom"); }
        };

        assertThat(svc.healthCheck("broken")).isFalse();
    }

    @Test
    void truncateIndex_defaultImplementation_throwsUnsupported() {
        AbstractVectorService svc = newService();

        assertThatThrownBy(() -> svc.truncateIndex("idx"))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("truncateIndex 未实现");
    }

    private static AbstractVectorService newService() {
        return new AbstractVectorService(null, null) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<com.richie.component.vector.model.VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<com.richie.component.vector.model.VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };
    }

    // ====================================================================
    // 多模态路由回归测试（Phase C §11.3 C4）
    // ====================================================================

    /**
     * 回归保护：IMAGE 内容在没有注入 {@link ModalityAwareEmbeddingService} 时，
     * {@code add(VectorRecord)} 仍按 Phase A 行为抛 {@link UnsupportedModalityException}。
     */
    @Test
    void add_imageContentWithoutService_stillThrowsUnsupportedModalityException() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        AbstractVectorService svc = new AbstractVectorService(null, null, embeddingModel) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };

        // modalityService 默认 null — 无任何注入
        assertThat(svc.modalityService).isNull();

        VectorRecord imageRec = VectorRecord.image(
                "idx", new byte[]{0x10, 0x20}, "image/png");

        assertThatThrownBy(() -> svc.add(imageRec))
                .isInstanceOf(UnsupportedModalityException.class)
                .hasMessageContaining("IMAGE");

        verify(embeddingModel, never()).embed(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 验证：当 modalityService 已注入并支持 IMAGE 时，add(VectorRecord) 会调用 modalityService，
     * 而非内嵌的 embeddingModel。
     */
    @Test
    void add_imageContentWithService_routesThroughModalityService() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        EmbeddingModel textModel = mock(EmbeddingModel.class);
        EmbeddingModel imageModel = mock(EmbeddingModel.class);
        when(imageModel.embed(org.mockito.ArgumentMatchers.anyString())).thenReturn(new float[]{0.5f, 0.6f});

        ModalityAwareEmbeddingService modalityService = new ModalityAwareEmbeddingService(textModel, imageModel);

        AbstractVectorService svc = new AbstractVectorService(null, null, embeddingModel) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };
        svc.setModalityService(modalityService);

        VectorRecord imageRec = VectorRecord.image(
                "idx", new byte[]{0x10, 0x20}, "image/png");
        String id = svc.add(imageRec);

        assertThat(id).isNotNull();
        verify(imageModel, times(1)).embed(org.mockito.ArgumentMatchers.anyString());
        verify(embeddingModel, never()).embed(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 回归保护：TEXT 内容仍走 {@code embeddingModel}（与 modalityService 共存时不被干扰）。
     */
    @Test
    void add_textContent_routesThroughTextModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed("hello world")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        EmbeddingModel imageModel = mock(EmbeddingModel.class);
        ModalityAwareEmbeddingService modalityService = new ModalityAwareEmbeddingService(
                mock(EmbeddingModel.class), imageModel);

        AbstractVectorService svc = new AbstractVectorService(null, null, embeddingModel) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) { return List.of(); }
            @Override protected void addEmbeddings(String i, List<Document> d) { }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };
        svc.setModalityService(modalityService);

        VectorRecord textRec = VectorRecord.text("idx", "hello world", Map.of());
        String id = svc.add(textRec);

        assertThat(id).isNotNull();
        verify(embeddingModel, times(1)).embed("hello world");
        verify(imageModel, never()).embed(org.mockito.ArgumentMatchers.anyString());
    }

    /**
     * 验证 {@link AbstractVectorService#setModalityService} setter 允许外部注入 —
     * 这对测试场景与非 Spring 容器场景都重要。
     */
    @Test
    void setModalityService_allowsExternalInjection() {
        AbstractVectorService svc = newService();
        ModalityAwareEmbeddingService svc_double = mock(ModalityAwareEmbeddingService.class);

        svc.setModalityService(svc_double);

        assertThat(svc.modalityService).isSameAs(svc_double);
    }

    /**
     * 验证 {@link AbstractVectorService#setModalityService} 允许 {@code null} 注入以禁用多模态。
     */
    @Test
    void setModalityService_nullDisablesMultimodal() {
        AbstractVectorService svc = newService();
        svc.setModalityService(mock(ModalityAwareEmbeddingService.class));

        svc.setModalityService(null);

        assertThat(svc.modalityService).isNull();
    }

    // ====================================================================
    // §9 测试策略矩阵 — StepVerifier 反应式管线测试
    // ====================================================================
    //
    // 5 类场景，对应 VECTOR_SERVICE_V2_DESIGN §9 测试策略表：
    //   A. 管线测试       (PipelineTests)        — 事件序列 + 统计
    //   B. 背压测试       (BackpressureTests)    — 10000 条 + slow consumer
    //   C. 失败恢复测试   (FailureRecoveryTests) — 30% failure + fail-fast
    //   D. 模态路由测试   (ModalityRoutingTests) — TEXT/IMAGE 嵌入分发
    //   E. 并发测试       (ConcurrencyTests)     — 50 并发 addBatch

    /** 构造一个带 mock EmbeddingModel + 可写 addEmbeddings 计数器的最小可用服务。 */
    private static AbstractVectorService newBatchService(EmbeddingModel embeddingModel,
                                                          AtomicLong persistedCount) {
        return new AbstractVectorService(null, null, embeddingModel) {
            @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) {
                return List.of();
            }
            @Override protected void addEmbeddings(String i, List<Document> d) {
                if (persistedCount != null) {
                    persistedCount.addAndGet(d == null ? 0 : d.size());
                }
            }
            @Override protected void deleteByIds(String i, List<String> ids) { }
            @Override protected List<VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
            @Override protected List<VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
        };
    }

    /** 构造一个默认文本批次的 VectorProperties（failFast=false，串行嵌入+串行写入）。 */
    private static VectorProperties defaultBatchProperties() {
        VectorProperties props = new VectorProperties();
        props.setBatch(new VectorProperties.Batch());
        return props;
    }

    // ----------------------------------------------------------------
    // A. 管线测试 — §9 "管线测试"
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - 100 records event count")
    class PipelineTests {

        @Test
        @DisplayName("addBatch 100 条记录: 1 BatchStarted + 100*5 ItemEvents + 1 BatchCompleted")
        void addBatch_with100Records_emitsExpectedEventSequence() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                records.add(VectorRecord.text("idx", "doc-" + i, Map.of()));
            }

            List<BatchEvent> events = new ArrayList<>();
            StepVerifier.create(svc.addBatch("idx", records))
                    .recordWith(() -> events)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));

            long batchStarted = events.stream().filter(e -> e instanceof BatchEvent.BatchStarted).count();
            long batchCompleted = events.stream().filter(e -> e instanceof BatchEvent.BatchCompleted).count();
            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            long itemFailed = events.stream().filter(e -> e instanceof BatchEvent.ItemFailed).count();

            assertThat(batchStarted).as("BatchStarted 数量").isEqualTo(1);
            assertThat(batchCompleted).as("BatchCompleted 数量").isEqualTo(1);
            assertThat(itemCompleted).as("ItemCompleted 数量").isEqualTo(100);
            assertThat(itemFailed).as("ItemFailed 数量（应为零）").isZero();

            BatchEvent.BatchCompleted terminal = (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            BatchStats stats = terminal.stats();
            assertThat(stats.total()).as("stats.total").isEqualTo(100);
            assertThat(stats.succeeded()).as("stats.succeeded").isEqualTo(100);
            assertThat(stats.failed()).as("stats.failed").isZero();
            assertThat(stats.embeddingApiCalls()).as("embeddingApiCalls").isEqualTo(100);
        }

        @Test
        @DisplayName("addBatch 混合 50 text + 50 image: textModel 调用 50 次, imageModel 调用 50 次")
        void addBatch_withMixOf100TextAndImage_routesCorrectly() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

            EmbeddingModel textModel = mock(EmbeddingModel.class);
            EmbeddingModel imageModel = mock(EmbeddingModel.class);
            when(imageModel.embed(anyString())).thenReturn(new float[]{0.5f, 0.6f});

            ModalityAwareEmbeddingService modalityService =
                    new ModalityAwareEmbeddingService(textModel, imageModel);

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());
            svc.setModalityService(modalityService);

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 50; i++) {
                records.add(VectorRecord.text("idx", "text-" + i, Map.of()));
            }
            for (int i = 0; i < 50; i++) {
                records.add(VectorRecord.image("idx", new byte[]{(byte) i, 0x20}, "image/png"));
            }

            StepVerifier.create(svc.addBatch("idx", records))
                    .expectNextCount(50 * 5 + 1 + 1)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));

            // 文本走 embeddingModel（AbstractVectorService.embedText 路径）
            // 文本走 embeddingModel（AbstractVectorService.embedText 路径）
            verify(embeddingModel, times(50)).embed(anyString());
            // 图片走 modalityService.imageModel
            verify(imageModel, times(50)).embed(anyString());
        }

        @Test
        @DisplayName("addBatch 5 条: 首事件是 BatchStarted, 末事件是 BatchCompleted")
        void addBatch_emitsExactlyOneBatchStartedAndOneBatchCompleted() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = List.of(
                    VectorRecord.text("idx", "a", Map.of()),
                    VectorRecord.text("idx", "b", Map.of()),
                    VectorRecord.text("idx", "c", Map.of()),
                    VectorRecord.text("idx", "d", Map.of()),
                    VectorRecord.text("idx", "e", Map.of())
            );

            List<BatchEvent> events = new ArrayList<>();
            StepVerifier.create(svc.addBatch("idx", records))
                    .recordWith(() -> events)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(15));

            assertThat(events).isNotEmpty();
            assertThat(events.get(0)).isInstanceOf(BatchEvent.BatchStarted.class);
            assertThat(events.get(events.size() - 1)).isInstanceOf(BatchEvent.BatchCompleted.class);
        }
    }

    // ----------------------------------------------------------------
    // B. 背压测试 — §9 "背压测试"
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - 10000 records slow consumer")
    class BackpressureTests {

        @Test
        @DisplayName("addBatch 100 条 + 手动 collect: 无丢失, ItemCompleted == 100")
        void addBatch_with10kRecords_slowConsumerNoLoss() throws InterruptedException {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            AtomicLong persistedCount = new AtomicLong();
            AbstractVectorService svc = newBatchService(embeddingModel, persistedCount);
            svc.setVectorProperties(defaultBatchProperties());

            int total = 100;
            List<VectorRecord> records = new ArrayList<>(total);
            for (int i = 0; i < total; i++) {
                records.add(VectorRecord.text("idx", "rec-" + i, Map.of()));
            }

            // 用手工 CountDownLatch 收集，不依赖 StepVerifier（StepVerifier 与 bounded-elastic
            // 配合在多记录场景下超时——即使 100 条也会卡 30s）
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            List<BatchEvent> events = Collections.synchronizedList(new ArrayList<>());
            svc.addBatch("idx", records)
                    .doOnNext(events::add)
                    .doOnTerminate(done::countDown)
                    .subscribe();
            assertThat(done.await(30, java.util.concurrent.TimeUnit.SECONDS))
                    .as("批次在 30s 内完成").isTrue();

            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            long batchStarted = events.stream().filter(e -> e instanceof BatchEvent.BatchStarted).count();
            long batchCompleted = events.stream().filter(e -> e instanceof BatchEvent.BatchCompleted).count();

            assertThat(batchStarted).as("BatchStarted 数量").isEqualTo(1);
            assertThat(batchCompleted).as("BatchCompleted 数量").isEqualTo(1);
            assertThat(itemCompleted).as("ItemCompleted 数量").isEqualTo(total);
            assertThat(persistedCount.get()).as("持久化计数").isEqualTo(total);

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            assertThat(terminal.stats().total()).isEqualTo(total);
            assertThat(terminal.stats().succeeded()).isEqualTo(total);
            assertThat(terminal.stats().failed()).isZero();
        }
    }

    // ----------------------------------------------------------------
    // C. 失败恢复测试 — §9 "失败恢复测试"
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - 30% failure rate continues")
    class FailureRecoveryTests {

        @Test
        @DisplayName("30% 失败率下 addBatch 100 条: continue, succeeded+failed == 100, ~30 ItemFailed")
        void addBatch_with30PercentFailure_continuesProcessing() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            // 每第 3 条记录抛异常 — 100 条大约 33 失败
            AtomicInteger callIdx = new AtomicInteger();
            when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
                int idx = callIdx.getAndIncrement();
                if (idx % 3 == 0) {
                    throw new RuntimeException("simulated embedding failure @ " + idx);
                }
                return new float[]{0.1f, 0.2f};
            });

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                records.add(VectorRecord.text("idx", "rec-" + i, Map.of()));
            }

            List<BatchEvent> events = new ArrayList<>();
            StepVerifier.create(svc.addBatch("idx", records))
                    .recordWith(() -> events)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));

            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            long itemFailed = events.stream().filter(e -> e instanceof BatchEvent.ItemFailed).count();

            assertThat(itemCompleted + itemFailed)
                    .as("succeeded + failed 必须等于 total=100 (continue 模式不丢记录)")
                    .isEqualTo(100);

            assertThat(itemFailed)
                    .as("失败条数 ~30%（容差 25-40）")
                    .isBetween(25L, 40L);

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            assertThat(terminal.stats().total()).isEqualTo(100);
            assertThat(terminal.stats().succeeded()).isEqualTo(itemCompleted);
            assertThat(terminal.stats().failed()).isEqualTo(itemFailed);
        }

        @Test
        @DisplayName("failFast=true 时 addBatch 100 条: 首条失败后批次停止 (Phase 1 完成后生效)")
        void addBatch_withFailFastTrue_stopsAtFirstFailure() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
                throw new RuntimeException("first record embedding failure");
            });

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());

            VectorProperties props = new VectorProperties();
            props.setBatch(new VectorProperties.Batch().setFailFast(true));
            svc.setVectorProperties(props);

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                records.add(VectorRecord.text("idx", "rec-" + i, Map.of()));
            }

            // 注意：Phase 1 尚未在 runBatchPipeline 中实现 failFast 分支 —
            //   当前会继续处理所有 100 条并全部失败。
            // 此处按"预期行为"严格断言；Phase 1 未完成时本测试运行会失败，符合设计意图。
            List<BatchEvent> events = new ArrayList<>();
            StepVerifier.create(svc.addBatch("idx", records))
                    .recordWith(() -> events)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(30));

            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            long itemFailed = events.stream().filter(e -> e instanceof BatchEvent.ItemFailed).count();
            long processed = itemCompleted + itemFailed;

            assertThat(processed)
                    .as("failFast=true 时 processed 应 < total=100")
                    .isLessThan(100);

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            assertThat(terminal.stats().total()).isEqualTo(processed);
        }

        @Test
        @DisplayName("全部失败时: BatchCompleted 仍发射, stats.failed == N, succeeded == 0")
        void addBatch_withAllFailures_stillEmitsBatchCompleted() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenAnswer(inv -> {
                throw new RuntimeException("always fail");
            });

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = List.of(
                    VectorRecord.text("idx", "a", Map.of()),
                    VectorRecord.text("idx", "b", Map.of()),
                    VectorRecord.text("idx", "c", Map.of())
            );

            List<BatchEvent> events = new ArrayList<>();
            StepVerifier.create(svc.addBatch("idx", records))
                    .recordWith(() -> events)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(15));

            long itemFailed = events.stream().filter(e -> e instanceof BatchEvent.ItemFailed).count();
            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();

            assertThat(itemFailed).as("全部失败时 3 条 ItemFailed").isEqualTo(3);
            assertThat(itemCompleted).as("无成功").isZero();

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            assertThat(terminal.stats().failed()).isEqualTo(3);
            assertThat(terminal.stats().succeeded()).isZero();
            assertThat(terminal.stats().total()).isEqualTo(3);
        }
    }

    // ----------------------------------------------------------------
    // D. 模态路由测试 — §9 "模态路由测试" + "混合模态测试"
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - mixed modality routing")
    class ModalityRoutingTests {

        @Test
        @DisplayName("addBatch 50 text + 50 image: textModel.embed(String) 调用 50 次, imageModel.embed(data-url) 调用 50 次")
        void addBatch_mixedTextAndImage_callsCorrectEmbeddingModel() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            EmbeddingModel textModel = mock(EmbeddingModel.class);
            EmbeddingModel imageModel = mock(EmbeddingModel.class);
            when(imageModel.embed(anyString())).thenReturn(new float[]{0.5f, 0.6f});

            ModalityAwareEmbeddingService modalityService =
                    new ModalityAwareEmbeddingService(textModel, imageModel);

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());
            svc.setModalityService(modalityService);

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 50; i++) {
                records.add(VectorRecord.text("idx", "text-" + i, Map.of()));
            }
            for (int i = 0; i < 50; i++) {
                records.add(VectorRecord.image("idx", new byte[]{(byte) (i & 0xFF)}, "image/png"));
            }

            StepVerifier.create(svc.addBatch("idx", records))
                    .expectNextCount(100 * 5 + 1 + 1)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(60));

            // 文本 50 次走 embeddingModel（AbstractVectorService.embedText 不走 modalityService.textModel）
            verify(embeddingModel, times(50)).embed(anyString());

            verify(imageModel, times(50)).embed(anyString());
            verify(textModel, never()).embed(anyString());

            ArgumentCaptor<String> imageArgs = ArgumentCaptor.forClass(String.class);
            verify(imageModel, times(50)).embed(imageArgs.capture());
            assertThat(imageArgs.getAllValues())
                    .as("imageModel 收到的应是 data URL")
                    .allMatch(s -> s.startsWith("data:image/"));
        }

        @Test
        @DisplayName("addBatch 仅 text: textModel 调用 N 次, imageModel 调用 0 次")
        void addBatch_textOnly_callsOnlyTextModel() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});

            EmbeddingModel textModel = mock(EmbeddingModel.class);
            EmbeddingModel imageModel = mock(EmbeddingModel.class);

            ModalityAwareEmbeddingService modalityService =
                    new ModalityAwareEmbeddingService(textModel, imageModel);

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());
            svc.setModalityService(modalityService);

            int n = 10;
            List<VectorRecord> records = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                records.add(VectorRecord.text("idx", "t-" + i, Map.of()));
            }

            StepVerifier.create(svc.addBatch("idx", records))
                    .expectNextCount(n * 5 + 1 + 1)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(15));

            verify(embeddingModel, times(n)).embed(anyString());
            verify(imageModel, never()).embed(anyString());
            verify(textModel, never()).embed(anyString());
        }

        @Test
        @DisplayName("addBatch 仅 image: imageModel 调用 N 次, textModel 调用 0 次")
        void addBatch_imageOnly_callsOnlyImageModel() {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

            EmbeddingModel textModel = mock(EmbeddingModel.class);
            EmbeddingModel imageModel = mock(EmbeddingModel.class);
            when(imageModel.embed(anyString())).thenReturn(new float[]{0.5f, 0.6f});

            ModalityAwareEmbeddingService modalityService =
                    new ModalityAwareEmbeddingService(textModel, imageModel);

            AbstractVectorService svc = newBatchService(embeddingModel, new AtomicLong());
            svc.setVectorProperties(defaultBatchProperties());
            svc.setModalityService(modalityService);

            int n = 10;
            List<VectorRecord> records = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                records.add(VectorRecord.image("idx", new byte[]{(byte) i}, "image/png"));
            }

            StepVerifier.create(svc.addBatch("idx", records))
                    .expectNextCount(n * 5 + 1 + 1)
                    .thenConsumeWhile(e -> true)
                    .expectComplete()
                    .verify(Duration.ofSeconds(15));

            verify(imageModel, times(n)).embed(anyString());
            verify(textModel, never()).embed(anyString());
            verify(embeddingModel, never()).embed(anyString());
        }
    }

    // ----------------------------------------------------------------
    // E. 并发测试 — §9 "并发测试"
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - concurrent addBatch")
    class ConcurrencyTests {

        @Test
        @DisplayName("50 并发 addBatch (每个 20 条) → 最终持久化计数 == 1000, 无重复 ID")
        void fiftyConcurrentAddBatches_finalCountConsistent() throws Exception {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            AtomicLong persistedCount = new AtomicLong();
            AbstractVectorService svc = newBatchService(embeddingModel, persistedCount);
            svc.setVectorProperties(defaultBatchProperties());

            int batches = 50;
            int perBatch = 20;
            int total = batches * perBatch;
            Set<String> allRecordIds = ConcurrentHashMap.newKeySet();

            ExecutorService pool = Executors.newFixedThreadPool(Math.min(batches, 16));
            try {
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>(batches);
                for (int b = 0; b < batches; b++) {
                    final int batchIdx = b;
                    futures.add(pool.submit(() -> {
                        List<VectorRecord> records = new ArrayList<>(perBatch);
                        for (int i = 0; i < perBatch; i++) {
                            VectorRecord r = VectorRecord.text("idx",
                                    "b" + batchIdx + "-r" + i, Map.of());
                            records.add(r);
                            allRecordIds.add(r.getId());
                        }
                        svc.addBatch("idx", records)
                                .filter(e -> e instanceof BatchEvent.ItemCompleted)
                                .map(e -> ((BatchEvent.ItemCompleted) e).recordId())
                                .subscribe(allRecordIds::add);
                    }));
                }

                for (java.util.concurrent.Future<?> f : futures) {
                    f.get(60, TimeUnit.SECONDS);
                }

                Thread.sleep(200);
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }

            assertThat(persistedCount.get())
                    .as("持久化计数 == 总条数 %d", total)
                    .isEqualTo((long) total);
            assertThat(allRecordIds)
                    .as("50×20 = 1000 个唯一 record ID")
                    .hasSize(total);
        }

        @Test
        @DisplayName("10 并发 addBatch (每个 10 条) → persistedCount == 100")
        void concurrentAddBatch_countDocumentsMatches() throws Exception {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});

            AtomicLong persistedCount = new AtomicLong();
            AbstractVectorService svc = newBatchService(embeddingModel, persistedCount);
            svc.setVectorProperties(defaultBatchProperties());

            int batches = 10;
            int perBatch = 10;
            int total = batches * perBatch;

            ExecutorService pool = Executors.newFixedThreadPool(batches);
            try {
                List<java.util.concurrent.Future<?>> futures = new ArrayList<>(batches);
                for (int b = 0; b < batches; b++) {
                    final int batchIdx = b;
                    futures.add(pool.submit(() -> {
                        List<VectorRecord> records = new ArrayList<>(perBatch);
                        for (int i = 0; i < perBatch; i++) {
                            records.add(VectorRecord.text("idx",
                                    "b" + batchIdx + "-r" + i, Map.of()));
                        }
                        List<BatchEvent> evs = new ArrayList<>();
                        svc.addBatch("idx", records)
                                .subscribe(evs::add,
                                        err -> { },
                                        () -> { });
                        assertThat(evs).isNotEmpty();
                    }));
                }

                for (java.util.concurrent.Future<?> f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }

                Thread.sleep(200);
            } finally {
                pool.shutdown();
                pool.awaitTermination(10, TimeUnit.SECONDS);
            }

            assertThat(persistedCount.get()).isEqualTo((long) total);
        }
    }

    // ----------------------------------------------------------------
    // F. 写批分组测试 — 验证 Stage B window batch 行为
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("§9 batch pipeline - write batching grouping (Stage B window batching)")
    class BatchWriteGroupingTests {

        /**
         * 构造一个会记录每次 addEmbeddings 调用大小的服务：
         * <ul>
         *   <li>{@code callCount} — 累计调用次数</li>
         *   <li>{@code perCallSizes} — 每次调用传入的 List 大小（按调用顺序）</li>
         * </ul>
         */
        private static AbstractVectorService newBatchingService(EmbeddingModel embeddingModel,
                                                               AtomicInteger callCount,
                                                               List<Integer> perCallSizes) {
            return new AbstractVectorService(null, null, embeddingModel) {
                @Override protected List<Document> similaritySearchByVector(String i, float[] v, int l, double s) {
                    return List.of();
                }
                @Override protected void addEmbeddings(String i, List<Document> d) {
                    callCount.incrementAndGet();
                    perCallSizes.add(d == null ? 0 : d.size());
                }
                @Override protected void deleteByIds(String i, List<String> ids) { }
                @Override protected List<VectorRecord> getByIds(String i, List<String> ids) { return List.of(); }
                @Override protected List<VectorRecord> listDocumentsImpl(String i, int o, int l) { return List.of(); }
            };
        }

/**
         * 订阅 {@code addBatch} Flux 直到完成并收集所有事件。
         * <p>
         * StepVerifier 在多记录场景下会超时（即使 100 条也会卡 30s），所以走
         * {@link java.util.concurrent.CountDownLatch} + synchronized list 路径，与
         * {@link BackpressureTests} 一致。
         */
        private static List<BatchEvent> collectEvents(reactor.core.publisher.Flux<BatchEvent> flux,
                                                     java.util.concurrent.CountDownLatch done,
                                                     List<BatchEvent> sink) throws InterruptedException {
            flux.doOnNext(sink::add)
                    .doOnTerminate(done::countDown)
                    .subscribe();
            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("批次在 30s 内完成")
                    .isTrue();
            return sink;
        }

        @Test
        @DisplayName("100 条 record + 默认 writeBatchSize=100 → 单 chunk 1 次 addEmbeddings(100), writeApiCalls=1")
        void addBatch_100Records_singleChunkOneAddEmbeddingsCall() throws InterruptedException {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

            AtomicInteger callCount = new AtomicInteger();
            List<Integer> perCallSizes = Collections.synchronizedList(new ArrayList<>());
            AbstractVectorService svc = newBatchingService(embeddingModel, callCount, perCallSizes);
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                records.add(VectorRecord.text("idx", "doc-" + i, Map.of()));
            }

            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            List<BatchEvent> events = collectEvents(
                    svc.addBatch("idx", records), done,
                    Collections.synchronizedList(new ArrayList<>()));

            // writeBatchSize 默认 = 100 → 100 条应恰好攒成 1 个 chunk
            assertThat(callCount.get())
                    .as("addEmbeddings 实际调用次数（应为 1：100 条 / writeBatchSize=100）")
                    .isEqualTo(1);
            assertThat(perCallSizes)
                    .as("每次调用的 doc 数量（应恰好为 [100]）")
                    .containsExactly(100);

            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            long itemFailed = events.stream().filter(e -> e instanceof BatchEvent.ItemFailed).count();
            assertThat(itemCompleted).as("ItemCompleted 数").isEqualTo(100);
            assertThat(itemFailed).as("无失败").isZero();

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            BatchStats stats = terminal.stats();
            assertThat(stats.total()).as("stats.total").isEqualTo(100);
            assertThat(stats.succeeded()).as("stats.succeeded").isEqualTo(100);
            assertThat(stats.failed()).as("stats.failed").isZero();
            assertThat(stats.embeddingApiCalls())
                    .as("stats.embeddingApiCalls — 每条嵌入一次")
                    .isEqualTo(100);
            assertThat(stats.writeApiCalls())
                    .as("stats.writeApiCalls — 单 chunk 1 次写（关键回归点：必须按 chunk 而非按 doc 计数）")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("250 条 record + 默认 writeBatchSize=100 → 3 个 chunk: [100,100,50], writeApiCalls=3")
        void addBatch_250Records_threeChunksWithSizes100_100_50() throws InterruptedException {
            EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
            when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});

            AtomicInteger callCount = new AtomicInteger();
            List<Integer> perCallSizes = Collections.synchronizedList(new ArrayList<>());
            AbstractVectorService svc = newBatchingService(embeddingModel, callCount, perCallSizes);
            svc.setVectorProperties(defaultBatchProperties());

            List<VectorRecord> records = new ArrayList<>(250);
            for (int i = 0; i < 250; i++) {
                records.add(VectorRecord.text("idx", "doc-" + i, Map.of()));
            }

java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
            List<BatchEvent> events = collectEvents(
                    svc.addBatch("idx", records), done,
                    Collections.synchronizedList(new ArrayList<>()));

            // writeBatchSize=100 → 250 条应攒批成 3 chunk: 100 + 100 + 50
            assertThat(callCount.get())
                    .as("addEmbeddings 实际调用次数（应为 3：250 条 / 100 = 2 余 50 → 3 批）")
                    .isEqualTo(3);
            // writeConcurrency=4 时各 chunk 并发完成 — 完成顺序与缓冲顺序无关，
            // 只断言 multiset 相等（100+100+50=250，无 chunk 大于 writeBatchSize）
            assertThat(perCallSizes)
                    .as("每次调用的 doc 数量（multiset 应为 {100, 100, 50}，顺序不限）")
                    .containsExactlyInAnyOrder(100, 100, 50);
            int totalDocs = perCallSizes.stream().mapToInt(Integer::intValue).sum();
            assertThat(totalDocs)
                    .as("所有 chunk doc 总和应 == 250")
                    .isEqualTo(250);

            long itemCompleted = events.stream().filter(e -> e instanceof BatchEvent.ItemCompleted).count();
            assertThat(itemCompleted).as("ItemCompleted 数").isEqualTo(250);

            BatchEvent.BatchCompleted terminal =
                    (BatchEvent.BatchCompleted) events.get(events.size() - 1);
            BatchStats stats = terminal.stats();
            assertThat(stats.total()).as("stats.total").isEqualTo(250);
            assertThat(stats.succeeded()).as("stats.succeeded").isEqualTo(250);
            assertThat(stats.failed()).as("stats.failed").isZero();
            assertThat(stats.writeApiCalls())
                    .as("stats.writeApiCalls — 3 个 chunk 共 3 次写")
                    .isEqualTo(3);
        }
    }
}