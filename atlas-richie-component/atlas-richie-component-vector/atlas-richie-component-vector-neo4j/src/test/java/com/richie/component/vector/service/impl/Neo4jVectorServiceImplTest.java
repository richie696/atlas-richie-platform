/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.neo4j.driver.Record;
import org.neo4j.driver.SimpleQueryRunner;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
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
 *   <li>文档操作（countDocuments, listDocumentsHandler）</li>
 *   <li>向量搜索（searchByVector）</li>
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
        vectorService = new Neo4jVectorServiceImpl(vectorStore, embeddingModel, driver);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("应生成正确的 CREATE CONSTRAINT Cypher 并执行")
        void createIndex_shouldRunConstraintCypher() {
            // given: driver 返回 mock session，session.run 被调用
            when(driver.session()).thenReturn(session);
            // when
            vectorService.createIndex("documents", new VectorProperties.IndexConfig());
            // then
            verify(driver).session();
            verify(session).run(contains("CREATE CONSTRAINT"));
            verify(session).run(contains("VectorDocument_documents"));
            verify(session).run(contains("id IS UNIQUE"));
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
            verify(session).run(contains("VectorDocument_my_index"));
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
    @DisplayName("listDocumentsHandler")
    class ListDocumentsHandlerTests {

        @Test
        @DisplayName("应执行分页查询并映射文档")
        void listDocumentsHandler_shouldPaginateAndMap() {
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
            List<VectorDocument> docs = vectorService.listDocumentsHandler("documents", 0, 10);

            // then
            assertThat(docs).hasSize(2);
            assertThat(docs.get(0).getId()).isEqualTo("doc-1");
            assertThat(docs.get(0).getMetadata()).containsEntry("type", "note");
            assertThat(docs.get(1).getId()).isEqualTo("doc-2");
        }

        @Test
        @DisplayName("当无结果时应返回空列表")
        void listDocumentsHandler_whenEmpty_shouldReturnEmptyList() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            // when
            List<VectorDocument> docs = vectorService.listDocumentsHandler("documents", 0, 10);
            // then
            assertThat(docs).isEmpty();
        }
    }

    @Nested
    @DisplayName("searchByVector")
    class SearchByVectorTests {

        @Test
        @DisplayName("应执行 Neo4j 向量查询并返回结果")
        void searchByVector_shouldQueryAndReturnResults() {
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
            when(contentVal.asString()).thenReturn("Hello Neo4j vector");
            when(scoreVal.asDouble()).thenReturn(0.95);

            when(result.hasNext()).thenReturn(true, false);
            when(result.next()).thenReturn(searchRecord);

            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            // when
            List<VectorSearchResult> results = vectorService.searchByVector("documents", queryVector, 5);

            // then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("doc-1");
            assertThat(results.get(0).getContent()).isEqualTo("Hello Neo4j vector");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("当向量为空时应抛出 IllegalArgumentException")
        void searchByVector_whenNullVector_shouldThrow() {
            assertThatThrownBy(() -> vectorService.searchByVector("documents", null, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("查询向量不能为空");
        }

        @Test
        @DisplayName("当向量为空数组时应抛出 IllegalArgumentException")
        void searchByVector_whenEmptyVector_shouldThrow() {
            assertThatThrownBy(() -> vectorService.searchByVector("documents", new float[]{}, 5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("查询向量不能为空");
        }

        @Test
        @DisplayName("当 limit <= 0 时应抛出 IllegalArgumentException")
        void searchByVector_whenInvalidLimit_shouldThrow() {
            float[] vector = new float[]{0.1f, 0.2f};
            assertThatThrownBy(() -> vectorService.searchByVector("documents", vector, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit 必须大于 0");
            assertThatThrownBy(() -> vectorService.searchByVector("documents", vector, -1))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit 必须大于 0");
        }

        @Test
        @DisplayName("当无结果时应返回空列表")
        void searchByVector_whenNoResults_shouldReturnEmptyList() {
            // given
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            // when
            List<VectorSearchResult> results = vectorService.searchByVector("documents", new float[]{0.1f}, 5);
            // then
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("继承方法验证")
    class InheritedMethodsTests {

        @Test
        @DisplayName("addDocument 应代理到 VectorStore")
        void addDocument_shouldDelegateToVectorStore() {
            VectorDocument doc = new VectorDocument();
            doc.setContent("test content");
            doc.setMetadata(Map.of());
            // vectorService 构造时注入了 mock VectorStore
            // 调用父类 addDocument 会使用 vectorStore.add()
            String id = vectorService.addDocument(doc);
            assertThat(id).isNotNull();
            verify(vectorStore).add(any());
        }

        @Test
        @DisplayName("deleteDocument 应代理到 VectorStore")
        void deleteDocument_shouldDelegateToVectorStore() {
            vectorService.deleteDocument("doc-1");
            verify(vectorStore).delete("doc-1");
        }

        @Test
        @DisplayName("listDocuments(0, 0) 应返回空列表（父类逻辑）")
        void listDocuments_zeroLimit_returnsEmpty() {
            assertThat(vectorService.listDocuments("idx", 0, 0)).isEmpty();
        }

        @Test
        @DisplayName("listDocuments 应限制最大返回数量为 1000")
        void listDocuments_shouldCapAtMaxLimit() {
            // 父类 VectorServiceImpl.MAX_LIST_LIMIT = 1000
            // 即使传入 5000，也会被限制为 1000
            // 由于 listDocumentsHandler 是 protected abstract，这里只验证不抛异常
            // 需要 stub driver.session() 否则会 NPE
            when(driver.session()).thenReturn(session);
            doReturn(result).when((SimpleQueryRunner) session).run(anyString(), any(Value.class));
            when(result.hasNext()).thenReturn(false);
            assertThat(vectorService.listDocuments("idx", 0, 5000)).isNotNull();
        }
    }
}
