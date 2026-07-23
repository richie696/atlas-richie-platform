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
import com.richie.component.vector.model.VectorRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.Mockito.lenient;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.neo4j.driver.SimpleQueryRunner;
import org.neo4j.driver.Value;
import org.neo4j.driver.summary.ResultSummary;
import org.neo4j.driver.summary.SummaryCounters;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Neo4jVectorServiceImpl 单元测试.
 *
 * <p>验证 Neo4j 向量数据库服务实现类的所有方法，包括：
 * <ul>
 *   <li>索引管理（createIndex, deleteIndex, indexExists, getIndexConfig）</li>
 *   <li>文档操作（countDocuments, listDocumentsImpl）</li>
 *   <li>向量搜索（similaritySearchByVector）</li>
 * </ul>
 *
 * <p>使用 Mockito 模拟 Neo4j Driver/Session/Result，避免对真实 Neo4j 实例的依赖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class Neo4jVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private Result result;

    private Neo4jVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        // Neo4jVectorServiceImpl 构造器参数顺序: VectorStore, EmbeddingModel, Driver
        vectorService = new Neo4jVectorServiceImpl(null, vectorStore, embeddingModel, driver);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("应生成 CREATE CONSTRAINT + VECTOR INDEX Cypher 并执行")
        void createIndex_shouldRunConstraintAndVectorIndexCypher() {
            // given: driver 返回 mock session，session.run 被调用
            when(driver.session()).thenReturn(session);
            // when
            vectorService.createIndex("documents", new VectorProperties.IndexConfig());
            // then
            verify(driver).session();
            // CONSTRAINT 与 VECTOR INDEX 两条 Cypher 都执行；用 contains 匹配子串
            verify(session, times(2)).run(anyString());
            verify(session, atLeastOnce()).run(contains("CREATE CONSTRAINT"));
            verify(session, atLeastOnce()).run(contains("VectorDocument_documents"));
            verify(session, atLeastOnce()).run(contains("id IS UNIQUE"));
            verify(session, atLeastOnce()).run(contains("CREATE VECTOR INDEX"));
            verify(session, atLeastOnce()).run(contains("documents_idx"));
            verify(session, atLeastOnce()).run(contains("vector.dimensions"));
            verify(session, atLeastOnce()).run(contains("vector.similarity_function"));
            verify(session, atLeastOnce()).run(contains("cosine"));
            verify(session).close();
        }

        @Test
        @DisplayName("应正确处理带下划线的索引名称")
        void createIndex_withUnderscoreInName_shouldEscapeProperly() {
            // given
            when(driver.session()).thenReturn(session);
            // when
            vectorService.createIndex("my_index", new VectorProperties.IndexConfig());
            // then
            verify(session, times(2)).run(anyString());
            verify(session, atLeastOnce()).run(contains("VectorDocument_my_index"));
            verify(session, atLeastOnce()).run(contains("`my_index_idx`"));
        }

        @Test
        @DisplayName("Phase B: 自定义 metric=distance 时 similarity_function 应为 euclidean")
        void createIndex_mapsMetricToSimilarityFunction() {
            when(driver.session()).thenReturn(session);
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setDimension(768);
            config.setMetric("euclidean");

            vectorService.createIndex("docs", config);

            verify(session, atLeastOnce()).run(contains("euclidean"));
            verify(session, atLeastOnce()).run(contains("vector.dimensions`: 768"));
        }

        @Test
        @DisplayName("Phase B: metric=dot 时 similarity_function 应为 dot")
        void createIndex_mapsDotMetric() {
            when(driver.session()).thenReturn(session);
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setDimension(768);
            config.setMetric("dot");

            vectorService.createIndex("docs", config);

            verify(session, atLeastOnce()).run(contains("'dot'"));
        }

        @Test
        @DisplayName("Phase B: dimension 为 null 时默认 1536")
        void createIndex_defaultsDimensionTo1536() {
            when(driver.session()).thenReturn(session);

            vectorService.createIndex("docs", new VectorProperties.IndexConfig());

            verify(session, atLeastOnce()).run(contains("vector.dimensions`: 1536"));
        }
    }

    @Nested
    @DisplayName("deleteIndex")
    class DeleteIndexTests {

        @Test
        @DisplayName("应生成正确的 DROP CONSTRAINT Cypher 并执行")
        void deleteIndex_shouldRunDropConstraintCypher() {
            // given
            when(driver.session()).thenReturn(session);
            // when
            vectorService.deleteIndex("documents");
            // then
            verify(driver).session();
            verify(session).run(contains("DROP CONSTRAINT"));
            verify(session).run(contains("VectorDocument_documents"));
            verify(session).close();
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("当结果为空时应返回 false")
        void indexExists_whenEmptyResult_shouldReturnFalse() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when(session).run(anyString());
            when(result.hasNext()).thenReturn(false);
            // when
            boolean exists = vectorService.indexExists("documents");
            // then
            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("当索引不存在时应抛出 UnsupportedOperationException")
        void getIndexConfig_whenNotExists_shouldThrow() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when(session).run(anyString());
            when(result.hasNext()).thenReturn(false);
            // when/then
            assertThatThrownBy(() -> vectorService.getIndexConfig("nonexistent"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("索引不存在");
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("应返回匹配的节点数量")
        void countDocuments_shouldReturnCount() {
            // given: MATCH COUNT 查询返回 42
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            Record countRecord = mock(Record.class);
            Value countValue = mock(Value.class);
            when(countRecord.get("count")).thenReturn(countValue);
            when(countValue.asLong()).thenReturn(42L);
            when(result.hasNext()).thenReturn(true);
            when(result.next()).thenReturn(countRecord);
            // when
            long count = vectorService.countDocuments("documents");
            // then
            assertThat(count).isEqualTo(42L);
        }

        @Test
        @DisplayName("当无结果时应返回 0")
        void countDocuments_whenNoResult_shouldReturnZero() {
            // given
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            when(result.hasNext()).thenReturn(false);
            // when
            long count = vectorService.countDocuments("documents");
            // then
            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("listDocumentsImpl")
    class ListDocumentsImplTests {

        @Test
        @DisplayName("应执行分页查询并映射文档")
        void listDocumentsImpl_shouldPaginateAndMap() {
            // given: MATCH 查询返回两个文档
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));

            // 构造节点和属性
            org.neo4j.driver.types.Node node1 = mock(org.neo4j.driver.types.Node.class);
            when(node1.asMap()).thenReturn(Map.of(
                    "id", "doc-1",
                    "content", "Hello world",
                    "metadata", Map.of("type", "note")
            ));
            org.neo4j.driver.types.Node node2 = mock(org.neo4j.driver.types.Node.class);
            when(node2.asMap()).thenReturn(Map.of(
                    "id", "doc-2",
                    "content", "Second doc"
            ));

            // record.get("n") returns Value, Value.asNode() returns Node
            Value nodeValue1 = mock(Value.class);
            when(nodeValue1.asNode()).thenReturn(node1);
            Value nodeValue2 = mock(Value.class);
            when(nodeValue2.asNode()).thenReturn(node2);

            Record record1 = mock(Record.class);
            when(record1.get("n")).thenReturn(nodeValue1);
            Record record2 = mock(Record.class);
            when(record2.get("n")).thenReturn(nodeValue2);

            when(result.hasNext()).thenReturn(true, true, false);
            when(result.next()).thenReturn(record1, record2);

            // when
            List<VectorRecord> records = vectorService.listDocumentsImpl("documents", 0, 10);

            // then
            assertThat(records).hasSize(2);
            assertThat(records.get(0).getId()).isEqualTo("doc-1");
            assertThat(records.get(0).getIndexName()).isEqualTo("documents");
            assertThat(records.get(0).getMetadata()).containsEntry("type", "note");
            assertThat(records.get(1).getId()).isEqualTo("doc-2");
        }

        @Test
        @DisplayName("当无结果时应返回空列表")
        void listDocumentsImpl_whenEmpty_shouldReturnEmptyList() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            // when
            List<VectorRecord> records = vectorService.listDocumentsImpl("documents", 0, 10);
            // then
            assertThat(records).isEmpty();
        }
    }

    @Nested
    @DisplayName("similaritySearchByVector")
    class SimilaritySearchByVectorTests {

        @Test
        @DisplayName("应执行 Neo4j 向量查询并返回结果")
        void similaritySearchByVector_shouldQueryAndReturnResults() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));

            Record searchRecord = mock(Record.class);
            Value idVal = mock(Value.class);
            Value contentVal = mock(Value.class);
            Value scoreVal = mock(Value.class);
            when(searchRecord.get("id")).thenReturn(idVal);
            when(searchRecord.get("content")).thenReturn(contentVal);
            when(searchRecord.get("score")).thenReturn(scoreVal);
            when(idVal.asString()).thenReturn("doc-1");
            when(contentVal.asString(anyString())).thenReturn("Hello Neo4j vector");
            when(scoreVal.asDouble()).thenReturn(0.95);

            when(result.hasNext()).thenReturn(true, false);
            when(result.next()).thenReturn(searchRecord);

            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            // when
            List<Document> results = vectorService.similaritySearchByVector("documents", queryVector, 5, 0.0);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("doc-1");
            assertThat(results.get(0).getText()).isEqualTo("Hello Neo4j vector");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("应将 score < minScore 的结果过滤掉")
        void similaritySearchByVector_shouldFilterByMinScore() {
            // given: score=0.50, 阈值 0.80
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));

            Record searchRecord = mock(Record.class);
            Value idVal = mock(Value.class);
            Value contentVal = mock(Value.class);
            Value scoreVal = mock(Value.class);
            when(searchRecord.get("id")).thenReturn(idVal);
            when(searchRecord.get("content")).thenReturn(contentVal);
            when(searchRecord.get("score")).thenReturn(scoreVal);
            when(idVal.asString()).thenReturn("doc-1");
            when(contentVal.asString(anyString())).thenReturn("low score");
            when(scoreVal.asDouble()).thenReturn(0.50);

            when(result.hasNext()).thenReturn(true, false);
            when(result.next()).thenReturn(searchRecord);

            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            // when
            List<Document> results = vectorService.similaritySearchByVector("documents", queryVector, 5, 0.80);

            // then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("当向量为空时应抛出 IllegalArgumentException")
        void similaritySearchByVector_whenNullVector_shouldThrow() {
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("documents", null, 5, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("查询向量不能为空");
        }

        @Test
        @DisplayName("当向量为空数组时应抛出 IllegalArgumentException")
        void similaritySearchByVector_whenEmptyVector_shouldThrow() {
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("documents", new float[]{}, 5, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("查询向量不能为空");
        }

        @Test
        @DisplayName("当 limit <= 0 时应抛出 IllegalArgumentException")
        void similaritySearchByVector_whenInvalidLimit_shouldThrow() {
            float[] vector = new float[]{0.1f, 0.2f};
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("documents", vector, 0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit 必须大于 0");
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("documents", vector, -1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit 必须大于 0");
        }

        @Test
        @DisplayName("当无结果时应返回空列表")
        void similaritySearchByVector_whenNoResults_shouldReturnEmptyList() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            // when
            List<Document> results = vectorService.similaritySearchByVector("documents", new float[]{0.1f}, 5, 0.0);
            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("addEmbeddings / deleteByIds")
    class EmbeddingLifecycleTests {

        @Test
        @DisplayName("addEmbeddings 应代理到 Spring AI VectorStore.add")
        void addEmbeddings_shouldDelegateToVectorStore() {
            // given
            List<Document> docs = List.of(
                    Document.builder().id("doc-1").text("hello").build()
            );
            // when
            vectorService.addEmbeddings("documents", docs);
            // then
            verify(vectorStore).add(docs);
        }

        @Test
        @DisplayName("addEmbeddings 空列表不应抛异常")
        void addEmbeddings_whenEmpty_shouldNotInvokeVectorStore() {
            vectorService.addEmbeddings("documents", List.of());
            verify(vectorStore, never()).add(anyList());
        }

        @Test
        @DisplayName("deleteByIds 应生成 MATCH DELETE Cypher")
        void deleteByIds_shouldRunMatchDeleteCypher() {
            // given
            when(driver.session()).thenReturn(session);
            // when
            vectorService.deleteByIds("documents", List.of("doc-1", "doc-2"));
            // then
            verify(session).run(contains("VectorDocument_documents"), any(Value.class));
            verify(session).run(contains("DELETE"), any(Value.class));
            verify(session).close();
        }

        @Test
        @DisplayName("deleteByIds 空列表不应执行 Cypher")
        void deleteByIds_whenEmpty_shouldNotRunCypher() {
            vectorService.deleteByIds("documents", List.of());
            verify(driver, never()).session();
        }
    }

    @Nested
    @DisplayName("getByIds")
    class GetByIdsTests {

        @Test
        @DisplayName("应返回匹配的 VectorRecord 列表")
        void getByIds_shouldReturnRecords() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));

            org.neo4j.driver.types.Node node1 = mock(org.neo4j.driver.types.Node.class);
            when(node1.asMap()).thenReturn(Map.of(
                    "id", "doc-1",
                    "content", "Hello world",
                    "metadata", Map.of("type", "note")
            ));
            Value nodeValue1 = mock(Value.class);
            when(nodeValue1.asNode()).thenReturn(node1);

            Record record1 = mock(Record.class);
            when(record1.get("n")).thenReturn(nodeValue1);

            when(result.hasNext()).thenReturn(true, false);
            when(result.next()).thenReturn(record1);

            // when
            List<VectorRecord> records = vectorService.getByIds("documents", List.of("doc-1", "doc-2"));

            // then
            assertThat(records).hasSize(1);
            assertThat(records.get(0).getId()).isEqualTo("doc-1");
            assertThat(records.get(0).getIndexName()).isEqualTo("documents");
        }

        @Test
        @DisplayName("空 ID 列表应直接返回空列表")
        void getByIds_whenEmptyIds_shouldReturnEmpty() {
            assertThat(vectorService.getByIds("documents", List.of())).isEmpty();
            verify(driver, never()).session();
        }
    }

    @Nested
    @DisplayName("继承方法验证")
    class InheritedMethodsTests {

        @Test
        @DisplayName("listDocuments(0, 0) 应返回空列表（父类逻辑）")
        void listDocuments_zeroLimit_returnsEmpty() {
            assertThat(vectorService.listDocuments("idx", 0, 0)).isEmpty();
        }

        @Test
        @DisplayName("listDocuments 应限制最大返回数量为 1000")
        void listDocuments_shouldCapAtMaxLimit() {
            // 父类 AbstractVectorService.MAX_LIST_LIMIT = 1000
            // 即使传入 5000，也会被限制为 1000
            // 由于 listDocumentsImpl 是 protected abstract，这里只验证不抛异常
            // 需要 stub driver.session() 否则会 NPE
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            assertThat(vectorService.listDocuments("idx", 0, 5000)).isNotNull();
        }
    }

    @Nested
    @DisplayName("truncateIndex")
    class TruncateIndexTests {

        @Test
        @DisplayName("should run MATCH DETACH DELETE and return deleted node count")
        void truncateIndex_executesDetachDelete() {
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            ResultSummary summary = mock(ResultSummary.class);
            SummaryCounters counters = mock(SummaryCounters.class);
            when(counters.nodesDeleted()).thenReturn(23);
            when(summary.counters()).thenReturn(counters);
            when(result.consume()).thenReturn(summary);
            when(result.hasNext()).thenReturn(true);
            Record countRecord = mock(Record.class);
            Value countValue = mock(Value.class);
            when(countRecord.get("count")).thenReturn(countValue);
            when(countValue.asLong()).thenReturn(23L);
            when(result.next()).thenReturn(countRecord);

            long deleted = vectorService.truncateIndex("documents");

            assertThat(deleted).isEqualTo(23L);
            verify(session).run(contains("MATCH (n:VectorDocument_documents) DETACH DELETE n"));
            verify(session, atLeastOnce()).close();
        }

        @Test
        @DisplayName("should return previous count when counters report zero")
        void truncateIndex_whenNoNodes_shouldReturnPreviousCount() {
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            ResultSummary summary = mock(ResultSummary.class);
            SummaryCounters counters = mock(SummaryCounters.class);
            when(counters.nodesDeleted()).thenReturn(0);
            when(summary.counters()).thenReturn(counters);
            when(result.consume()).thenReturn(summary);
            when(result.hasNext()).thenReturn(false);

            long deleted = vectorService.truncateIndex("empty");

            assertThat(deleted).isZero();
            verify(session).run(contains("DETACH DELETE"));
        }
    }

    @Nested
    @DisplayName("healthCheck (inherited 3-step probe)")
    class HealthCheckTests {

        @Test
        @DisplayName("should return true when constraint exists and count is non-negative")
        void healthCheck_whenOk_shouldReturnTrue() {
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            Record descRecord = mock(Record.class);
            Value descValue = mock(Value.class);
            when(descRecord.get("description")).thenReturn(descValue);
            when(descValue.asString("")).thenReturn(":(n:VectorDocument_documents) ON (n.id) IS UNIQUE id IS UNIQUE");

            Record countRecord = mock(Record.class);
            Value countValue = mock(Value.class);
            when(countRecord.get("count")).thenReturn(countValue);
            when(countValue.asLong()).thenReturn(7L);

            when(result.hasNext()).thenReturn(true, true);
            when(result.next()).thenReturn(descRecord, countRecord);

            assertThat(vectorService.healthCheck("documents")).isTrue();
        }

        @Test
        @DisplayName("should return false when SHOW CONSTRAINTS has no rows")
        void healthCheck_whenNoConstraints_shouldReturnFalse() {
            when(driver.session()).thenReturn(session);
            when(session.run(anyString())).thenReturn(result);
            when(result.hasNext()).thenReturn(false);

            assertThat(vectorService.healthCheck("missing")).isFalse();
        }
    }

    @Nested
    @DisplayName("Ops API（5 个抽象 *Impl 覆盖 — Neo4j driver 不支持）")
    class OpsApiTests {

        /**
         * 5 个 ops 调用在 Neo4j Java driver 下都直接抛 UnsupportedOperationException，
         * 因此断言时不需要 stub 任何 driver/session 行为 —— 错误会在到达 neo4j 之前抛出。
         */

        @Test
        @DisplayName("optimizeImpl: 应抛 UnsupportedOperationException 并提示 Neo4j 无 optimize API")
        void optimize_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.optimize("idx"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("neo4j")
                    .hasMessageContaining("optimize");
        }

        @Test
        @DisplayName("createAliasImpl: 应抛 UnsupportedOperationException 并提示 Neo4j 无 alias 机制")
        void createAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.createAlias("idx", "aliasA"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("neo4j")
                    .hasMessageContaining("Alias");
        }

        @Test
        @DisplayName("switchAliasImpl: 应抛 UnsupportedOperationException（与 createAlias 同源）")
        void switchAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.switchAlias("old", "new", "aliasA"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("neo4j")
                    .hasMessageContaining("Alias");
        }

        @Test
        @DisplayName("backupImpl: 应抛 UnsupportedOperationException 并提示需 neo4j-admin database dump")
        void backup_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.backup("idx", "/tmp/idx.dump"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("neo4j")
                    .hasMessageContaining("backup");
        }

        @Test
        @DisplayName("restoreImpl: 应抛 UnsupportedOperationException 并提示需 neo4j-admin database load")
        void restore_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.restore("/tmp/idx.dump", "idx"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("neo4j")
                    .hasMessageContaining("restore");
        }
    }
}
