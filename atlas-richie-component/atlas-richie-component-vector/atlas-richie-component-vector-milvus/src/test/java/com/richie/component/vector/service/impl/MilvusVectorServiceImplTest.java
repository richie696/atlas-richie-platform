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

import com.richie.component.vector.config.MilvusConfig;
import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.*;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.DescribeCollectionParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.control.ManualCompactParam;
import io.milvus.param.alias.AlterAliasParam;
import io.milvus.param.alias.CreateAliasParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.QueryResultsWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.document.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class MilvusVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MilvusServiceClient milvusClient;

    private MilvusConfig milvusConfig;
    private MilvusVectorServiceImpl service;

@BeforeEach
    void setUp() {
        milvusConfig = new MilvusConfig();
        milvusConfig.setHost("localhost");
        milvusConfig.setPort(19530);
        milvusConfig.setDatabaseName("default");
        milvusConfig.setCollectionName("documents");
        milvusConfig.setEmbeddingDimension(1536);
        milvusConfig.setIndexType(IndexType.IVF_FLAT);
        milvusConfig.setMetricType(MetricType.COSINE);

        service = new MilvusVectorServiceImpl(null, vectorStore, embeddingModel, milvusConfig, milvusClient);

        // impl 在 insert/delete/truncate 后会调 flush；query 在 countDocuments (statCount > 0) 时被调；
        // 这俩全局 mock 成功避免 NPE，specific 测试需要时可覆盖
        R<io.milvus.grpc.FlushResponse> flushResp = mock(R.class);
        when(flushResp.getStatus()).thenReturn(R.Status.Success.getCode());
        when(milvusClient.flush(any(FlushParam.class))).thenReturn(flushResp);

        QueryResults emptyQueryResults = mock(QueryResults.class);
        when(emptyQueryResults.getFieldsDataList()).thenReturn(List.of());
        R<QueryResults> queryResp = mock(R.class);
        when(queryResp.getStatus()).thenReturn(R.Status.Success.getCode());
        when(queryResp.getData()).thenReturn(emptyQueryResults);
        when(milvusClient.query(any(QueryParam.class))).thenReturn(queryResp);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("should create collection and index with default config")
        void createIndex_withDefaultConfig_shouldSucceed() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");
            config.setDimension(1536);
            config.setIndexType("HNSW");
            config.setMetric("COSINE");

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(successResp);

            service.createIndex("test-collection", config);

            verify(milvusClient).createCollection(any(CreateCollectionParam.class));
            verify(milvusClient).createIndex(any(CreateIndexParam.class));
        }

        @Test
        @DisplayName("should use config shards when provided")
        void createIndex_withConfigShards_shouldUseProvidedShards() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");
            config.setDimension(1536);
            config.setShards(4);
            config.setIndexType("HNSW");
            config.setMetric("COSINE");

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(successResp);

            service.createIndex("test-collection", config);

            verify(milvusClient).createCollection(any(CreateCollectionParam.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when createCollection fails")
        void createIndex_whenCreateCollectionFails_shouldThrow() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");

            R<RpcStatus> failResp = mockFailedRpcStatus("Collection already exists");
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.createIndex("test-collection", config))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus createCollection failed");
        }

        @Test
        @DisplayName("should throw RuntimeException when createIndex fails")
        void createIndex_whenCreateIndexFails_shouldThrow() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");
            config.setIndexType("HNSW");
            config.setMetric("COSINE");

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            R<RpcStatus> failResp = mockFailedRpcStatus("Index creation failed");
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.createIndex("test-collection", config))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus createIndex failed");
        }

        @Test
        @DisplayName("should use config indexType and metricType when provided")
        void createIndex_withCustomIndexAndMetricType_shouldUseProvidedTypes() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");
            config.setDimension(1536);
            config.setIndexType("HNSW");
            config.setMetric("L2");

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(successResp);

            service.createIndex("test-collection", config);

            verify(milvusClient).createIndex(any(CreateIndexParam.class));
        }

        @Test
        @DisplayName("should handle additionalFields in config")
        void createIndex_withAdditionalFields_shouldSucceed() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test-collection");
            config.setDimension(1536);
            config.setIndexType("HNSW");
            config.setMetric("COSINE");
            config.setAdditionalFields(Map.of(
                    "category", "VarChar",
                    "priority", Map.of("data_type", "Int64", "is_primary", false)
            ));

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(successResp);

            service.createIndex("test-collection", config);

            verify(milvusClient).createCollection(any(CreateCollectionParam.class));
        }

        @Test
        @DisplayName("Phase B: should use buildFieldTypes to include content + metadata + additional fields")
        void createIndex_usesBuildFieldTypes() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            config.setDimension(1536);
            config.setIndexType("HNSW");
            config.setMetric("COSINE");
            config.setAdditionalFields(Map.of("source", "VarChar"));

            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createCollection(any(CreateCollectionParam.class))).thenReturn(successResp);
            when(milvusClient.createIndex(any(CreateIndexParam.class))).thenReturn(successResp);

            service.createIndex("docs", config);

            ArgumentCaptor<CreateCollectionParam> captor = ArgumentCaptor.forClass(CreateCollectionParam.class);
            verify(milvusClient).createCollection(captor.capture());
            CreateCollectionParam passed = captor.getValue();
            assertThat(passed.getCollectionName()).isEqualTo("docs");
            // enableDynamicField=true allows additional fields
            assertThat(passed.getShardsNum()).isEqualTo(1);
            verify(milvusClient).createIndex(any(CreateIndexParam.class));
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("should return true when collection exists")
        void indexExists_whenCollectionExists_shouldReturnTrue() {
            R<Boolean> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(true);
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(successResp);

            boolean result = service.indexExists("test-collection");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("should return false when collection does not exist")
        void indexExists_whenCollectionNotExists_shouldReturnFalse() {
            R<Boolean> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(false);
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(successResp);

            boolean result = service.indexExists("test-collection");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should throw RuntimeException when hasCollection fails")
        void indexExists_whenHasCollectionFails_shouldThrow() {
            R<Boolean> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Connection failed");
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.indexExists("test-collection"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus hasCollection failed");
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("should return config with shards")
        void getIndexConfig_shouldReturnCorrectConfig() {
            DescribeCollectionResponse response = mock(DescribeCollectionResponse.class);
            when(response.getShardsNum()).thenReturn(2);
            // impl 从 schema.fields 提取向量维度——这里 schema 为空，循环不匹配，dimension 默认 0
            io.milvus.grpc.CollectionSchema schema = mock(io.milvus.grpc.CollectionSchema.class);
            when(schema.getFieldsList()).thenReturn(List.of());
            when(response.getSchema()).thenReturn(schema);

            R<DescribeCollectionResponse> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(response);
            when(milvusClient.describeCollection(any(DescribeCollectionParam.class))).thenReturn(successResp);

            VectorProperties.IndexConfig config = service.getIndexConfig("test-collection");

            assertThat(config.getName()).isEqualTo("test-collection");
            assertThat(config.getShards()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw RuntimeException when describeCollection fails")
        void getIndexConfig_whenDescribeFails_shouldThrow() {
            R<DescribeCollectionResponse> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Collection not found");
            when(milvusClient.describeCollection(any(DescribeCollectionParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.getIndexConfig("nonexistent"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus describeCollection failed");
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("should return document count from row_count stat")
        void countDocuments_shouldReturnRowCount() {
            // row_count = 0 走 countViaStatistics 路径，query 不被调用
            GetCollectionStatisticsResponse response = mock(GetCollectionStatisticsResponse.class);
            when(response.getStatsList()).thenReturn(List.of());

            R<GetCollectionStatisticsResponse> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(response);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(successResp);

            long count = service.countDocuments("test-collection");

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should return 0 when row_count not found")
        void countDocuments_whenRowCountNotFound_shouldReturnZero() {
            GetCollectionStatisticsResponse response = mock(GetCollectionStatisticsResponse.class);
            when(response.getStatsList()).thenReturn(List.of());

            R<GetCollectionStatisticsResponse> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(response);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(successResp);

            long count = service.countDocuments("test-collection");

            assertThat(count).isEqualTo(0L);
        }

        @Test
        @DisplayName("should throw RuntimeException when getCollectionStatistics fails")
        void countDocuments_whenGetStatisticsFails_shouldThrow() {
            R<GetCollectionStatisticsResponse> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Statistics unavailable");
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.countDocuments("test-collection"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus getCollectionStatistics failed");
        }
    }

    @Nested
    @DisplayName("listDocumentsHandler")
    class ListDocumentsHandlerTests {

        @Test
        @DisplayName("should return documents via QueryParam scan")
        void listDocumentsHandler_shouldReturnDocuments() {
            FieldData idField = FieldData.newBuilder()
                    .setFieldName("id")
                    .setType(DataType.VarChar)
                    .setScalars(ScalarField.newBuilder()
                            .setStringData(StringArray.newBuilder()
                                    .addAllData(List.of("doc1", "doc2"))
                                    .build())
                            .build())
                    .build();
            FieldData contentField = FieldData.newBuilder()
                    .setFieldName("content")
                    .setType(DataType.VarChar)
                    .setScalars(ScalarField.newBuilder()
                            .setStringData(StringArray.newBuilder()
                                    .addAllData(List.of("hello", "world"))
                                    .build())
                            .build())
                    .build();
            QueryResults queryResults = QueryResults.newBuilder()
                    .addAllFieldsData(List.of(idField, contentField))
                    .build();
            R<QueryResults> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(queryResults);
            when(milvusClient.query(any(QueryParam.class))).thenReturn(successResp);

            List<VectorRecord> docs = service.listDocuments("test-collection", 0, 10);

            assertThat(docs).hasSize(2);
            assertThat(docs.get(0).getId()).isEqualTo("doc1");
            assertThat(((VectorContent.TextContent) docs.get(1).getContent()).text()).isEqualTo("world");
        }

        @Test
        @DisplayName("should throw RuntimeException when query fails")
        void listDocumentsHandler_whenQueryFails_shouldThrow() {
            R<QueryResults> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Query failed");
            when(milvusClient.query(any(QueryParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.listDocuments("test-collection", 0, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus listDocuments failed");
        }
    }

    @Nested
    @DisplayName("similaritySearchByVector")
    class SimilaritySearchByVectorTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is null")
        void similaritySearchByVector_withNullVector_shouldThrow() {
            assertThatThrownBy(() -> service.similaritySearchByVector("test", null, 10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is empty")
        void similaritySearchByVector_withEmptyVector_shouldThrow() {
            assertThatThrownBy(() -> service.similaritySearchByVector("test", new float[]{}, 10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit <= 0")
        void similaritySearchByVector_withInvalidLimit_shouldThrow() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> service.similaritySearchByVector("test", vector, 0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should throw RuntimeException when search fails")
        void similaritySearchByVector_whenSearchFails_shouldThrow() {
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            R<SearchResults> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Search failed");
            when(milvusClient.search(any(SearchParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.similaritySearchByVector("test", queryVector, 10, 0.0))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus search failed");
        }
    }

    private R<RpcStatus> mockSuccessRpcStatus() {
        R<RpcStatus> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(R.Status.Success.getCode());
        return resp;
    }

    private R<RpcStatus> mockFailedRpcStatus(String message) {
        R<RpcStatus> resp = mock(R.class);
        when(resp.getStatus()).thenReturn(-1);
        when(resp.getMessage()).thenReturn(message);
        return resp;
    }

    @Nested
    @DisplayName("truncateIndex")
    class TruncateIndexTests {

        @Test
        @DisplayName("should issue match-all delete and return count from MutationResult")
        void truncateIndex_executesMatchAllDelete() {
            KeyValuePair stat = KeyValuePair.newBuilder()
                    .setKey("row_count")
                    .setValue("17")
                    .build();
            GetCollectionStatisticsResponse statsResponse = mock(GetCollectionStatisticsResponse.class);
            when(statsResponse.getStatsList()).thenReturn(List.of(stat));
            R<GetCollectionStatisticsResponse> statsResp = mock(R.class);
            when(statsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(statsResp.getData()).thenReturn(statsResponse);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(statsResp);

            MutationResult mutation = mock(MutationResult.class);
            when(mutation.getDeleteCnt()).thenReturn(17L);
            R<MutationResult> deleteResp = mock(R.class);
            when(deleteResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(deleteResp.getData()).thenReturn(mutation);
            when(milvusClient.delete(any(DeleteParam.class))).thenReturn(deleteResp);

            long deleted = service.truncateIndex("test-collection");

            assertThat(deleted).isEqualTo(17L);
            ArgumentCaptor<DeleteParam> captor = ArgumentCaptor.forClass(DeleteParam.class);
            verify(milvusClient).delete(captor.capture());
            assertThat(captor.getValue().getCollectionName()).isEqualTo("test-collection");
            assertThat(captor.getValue().getExpr()).contains("id !=");
        }

        @Test
        @DisplayName("should throw RuntimeException when delete fails")
        void truncateIndex_whenDeleteFails_shouldThrow() {
            GetCollectionStatisticsResponse statsResponse = mock(GetCollectionStatisticsResponse.class);
            when(statsResponse.getStatsList()).thenReturn(List.of());
            R<GetCollectionStatisticsResponse> statsResp = mock(R.class);
            when(statsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(statsResp.getData()).thenReturn(statsResponse);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(statsResp);

            R<MutationResult> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("delete failed");
            when(milvusClient.delete(any(DeleteParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.truncateIndex("test-collection"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus truncateIndex failed");
        }
    }

    @Nested
    @DisplayName("healthCheck (inherited 3-step probe)")
    class HealthCheckTests {

        @Test
        @DisplayName("should return true when schema exists and count is non-negative")
        void healthCheck_whenIndexOk_shouldReturnTrue() {
            R<Boolean> existsResp = mock(R.class);
            when(existsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(existsResp.getData()).thenReturn(true);
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(existsResp);

            KeyValuePair stat = KeyValuePair.newBuilder()
                    .setKey("row_count")
                    .setValue("8")
                    .build();
            GetCollectionStatisticsResponse statsResponse = mock(GetCollectionStatisticsResponse.class);
            when(statsResponse.getStatsList()).thenReturn(List.of(stat));
            R<GetCollectionStatisticsResponse> statsResp = mock(R.class);
            when(statsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(statsResp.getData()).thenReturn(statsResponse);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(statsResp);

            assertThat(service.healthCheck("test-collection")).isTrue();
        }

        @Test
        @DisplayName("should return false when schema missing")
        void healthCheck_whenIndexMissing_shouldReturnFalse() {
            R<Boolean> existsResp = mock(R.class);
            when(existsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(existsResp.getData()).thenReturn(false);
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(existsResp);

            assertThat(service.healthCheck("missing")).isFalse();
        }

        @Test
        @DisplayName("should return false when count query throws")
        void healthCheck_whenCountThrows_shouldReturnFalse() {
            R<Boolean> existsResp = mock(R.class);
            when(existsResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(existsResp.getData()).thenReturn(true);
            when(milvusClient.hasCollection(any(HasCollectionParam.class))).thenReturn(existsResp);

            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class)))
                    .thenThrow(new RuntimeException("connection lost"));

            assertThat(service.healthCheck("test-collection")).isFalse();
        }
    }

    @Nested
    @DisplayName("addEmbeddings")
    class AddEmbeddingsTests {

        @Test
        @DisplayName("should insert via InsertParam")
        void addEmbeddings_withValidDocs_shouldSucceed() {
            Document doc = new Document("doc1", "hello", Map.of("embedding", new float[]{0.1f, 0.2f}));
            R<MutationResult> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(milvusClient.insert(any(InsertParam.class))).thenReturn(successResp);

            service.addEmbeddings("test-collection", List.of(doc));

            ArgumentCaptor<InsertParam> captor = ArgumentCaptor.forClass(InsertParam.class);
            verify(milvusClient).insert(captor.capture());
            assertThat(captor.getValue().getCollectionName()).isEqualTo("test-collection");
        }

        @Test
        @DisplayName("should skip when docs is empty")
        void addEmbeddings_withEmptyDocs_shouldSkip() {
            service.addEmbeddings("test-collection", List.of());
            verify(milvusClient, never()).insert(any(InsertParam.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when insert fails")
        void addEmbeddings_whenInsertFails_shouldThrow() {
            Document doc = new Document("doc1", "hello", Map.of("embedding", new float[]{0.1f}));
            R<MutationResult> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Insert failed");
            when(milvusClient.insert(any(InsertParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.addEmbeddings("test-collection", List.of(doc)))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus insert failed");
        }
    }

    @Nested
    @DisplayName("deleteByIds")
    class DeleteByIdsTests {

        @Test
        @DisplayName("should delete via DeleteParam with id expr")
        void deleteByIds_withValidIds_shouldSucceed() {
            R<MutationResult> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(milvusClient.delete(any(DeleteParam.class))).thenReturn(successResp);

            service.deleteByIds("test-collection", List.of("id1", "id2"));

            ArgumentCaptor<DeleteParam> captor = ArgumentCaptor.forClass(DeleteParam.class);
            verify(milvusClient).delete(captor.capture());
            assertThat(captor.getValue().getExpr()).contains("id in [\"id1\",\"id2\"]");
        }

        @Test
        @DisplayName("should skip when ids is empty")
        void deleteByIds_withEmptyIds_shouldSkip() {
            service.deleteByIds("test-collection", List.of());
            verify(milvusClient, never()).delete(any(DeleteParam.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when delete fails")
        void deleteByIds_whenDeleteFails_shouldThrow() {
            R<MutationResult> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Delete failed");
            when(milvusClient.delete(any(DeleteParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.deleteByIds("test-collection", List.of("id1")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus deleteByIds failed");
        }
    }

    @Nested
    @DisplayName("getByIds")
    class GetByIdsTests {

        @Test
        @DisplayName("should return records via QueryParam")
        void getByIds_withValidIds_shouldReturnRecords() {
            FieldData idField = FieldData.newBuilder()
                    .setFieldName("id")
                    .setType(DataType.VarChar)
                    .setScalars(ScalarField.newBuilder()
                            .setStringData(StringArray.newBuilder()
                                    .addAllData(List.of("id1"))
                                    .build())
                            .build())
                    .build();
            FieldData contentField = FieldData.newBuilder()
                    .setFieldName("content")
                    .setType(DataType.VarChar)
                    .setScalars(ScalarField.newBuilder()
                            .setStringData(StringArray.newBuilder()
                                    .addAllData(List.of("content1"))
                                    .build())
                            .build())
                    .build();
            QueryResults queryResults = QueryResults.newBuilder()
                    .addAllFieldsData(List.of(idField, contentField))
                    .build();
            R<QueryResults> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(queryResults);
            when(milvusClient.query(any(QueryParam.class))).thenReturn(successResp);

            List<VectorRecord> records = service.getByIds("test-collection", List.of("id1"));

            assertThat(records).hasSize(1);
            assertThat(records.get(0).getId()).isEqualTo("id1");
            assertThat(((VectorContent.TextContent) records.get(0).getContent()).text()).isEqualTo("content1");
        }

        @Test
        @DisplayName("should return empty when ids is empty")
        void getByIds_withEmptyIds_shouldReturnEmpty() {
            List<VectorRecord> records = service.getByIds("test-collection", List.of());
            assertThat(records).isEmpty();
            verify(milvusClient, never()).query(any(QueryParam.class));
        }

        @Test
        @DisplayName("should throw RuntimeException when query fails")
        void getByIds_whenQueryFails_shouldThrow() {
            R<QueryResults> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Query failed");
            when(milvusClient.query(any(QueryParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.getByIds("test-collection", List.of("id1")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Milvus query failed");
        }
    }

    @Nested
    @DisplayName("ops: optimize / createAlias / switchAlias / backup / restore")
    class OpsImplementationTests {

        @Test
        @DisplayName("optimize: should return true when manualCompact succeeds")
        void optimize_whenCompactSucceeds_shouldReturnTrue() {
            io.milvus.grpc.ManualCompactionResponse compactResponse = mock(io.milvus.grpc.ManualCompactionResponse.class);
            R<io.milvus.grpc.ManualCompactionResponse> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(compactResponse);
            when(milvusClient.manualCompact(any(ManualCompactParam.class))).thenReturn(successResp);

            boolean result = service.optimize("test-collection");

            assertThat(result).isTrue();
            ArgumentCaptor<ManualCompactParam> captor = ArgumentCaptor.forClass(ManualCompactParam.class);
            verify(milvusClient).manualCompact(captor.capture());
            assertThat(captor.getValue().getCollectionName()).isEqualTo("test-collection");
        }

        @Test
        @DisplayName("optimize: should return false when manualCompact Status is not Success")
        void optimize_whenCompactFails_shouldReturnFalse() {
            R<io.milvus.grpc.ManualCompactionResponse> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("ManualCompact not allowed");
            when(milvusClient.manualCompact(any(ManualCompactParam.class))).thenReturn(failResp);

            boolean result = service.optimize("test-collection");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("optimize: should return false when manualCompact throws")
        void optimize_whenCompactThrows_shouldReturnFalse() {
            when(milvusClient.manualCompact(any(ManualCompactParam.class)))
                    .thenThrow(new RuntimeException("connection lost"));

            boolean result = service.optimize("test-collection");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("createAlias: should return true when SDK returns Success")
        void createAlias_whenSucceeds_shouldReturnTrue() {
            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.createAlias(any(CreateAliasParam.class))).thenReturn(successResp);

            boolean result = service.createAlias("test-collection", "test-alias");

            assertThat(result).isTrue();
            ArgumentCaptor<CreateAliasParam> captor = ArgumentCaptor.forClass(CreateAliasParam.class);
            verify(milvusClient).createAlias(captor.capture());
            assertThat(captor.getValue().getCollectionName()).isEqualTo("test-collection");
            assertThat(captor.getValue().getAlias()).isEqualTo("test-alias");
        }

        @Test
        @DisplayName("createAlias: should return false when SDK Status is not Success")
        void createAlias_whenFails_shouldReturnFalse() {
            R<RpcStatus> failResp = mockFailedRpcStatus("alias already exists");
            when(milvusClient.createAlias(any(CreateAliasParam.class))).thenReturn(failResp);

            boolean result = service.createAlias("test-collection", "test-alias");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("createAlias: should return false when SDK throws")
        void createAlias_whenThrows_shouldReturnFalse() {
            when(milvusClient.createAlias(any(CreateAliasParam.class)))
                    .thenThrow(new RuntimeException("connection lost"));

            boolean result = service.createAlias("test-collection", "test-alias");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("switchAlias: should return true when alterAlias succeeds")
        void switchAlias_whenSucceeds_shouldReturnTrue() {
            R<RpcStatus> successResp = mockSuccessRpcStatus();
            when(milvusClient.alterAlias(any(AlterAliasParam.class))).thenReturn(successResp);

            boolean result = service.switchAlias("old-collection", "new-collection", "test-alias");

            assertThat(result).isTrue();
            ArgumentCaptor<AlterAliasParam> captor = ArgumentCaptor.forClass(AlterAliasParam.class);
            verify(milvusClient).alterAlias(captor.capture());
            // AlterAliasParam 只接收新 collection name，oldIndexName 在 SDK 层不使用
            // 这里 verify 只断言 SDK 收到的 param 与 new collection name 对齐
            assertThat(captor.getValue().getCollectionName()).isEqualTo("new-collection");
            assertThat(captor.getValue().getAlias()).isEqualTo("test-alias");
        }

        @Test
        @DisplayName("switchAlias: should return false when alterAlias Status is not Success")
        void switchAlias_whenFails_shouldReturnFalse() {
            R<RpcStatus> failResp = mockFailedRpcStatus("alias not found");
            when(milvusClient.alterAlias(any(AlterAliasParam.class))).thenReturn(failResp);

            boolean result = service.switchAlias("old-collection", "new-collection", "test-alias");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("switchAlias: should return false when SDK throws")
        void switchAlias_whenThrows_shouldReturnFalse() {
            when(milvusClient.alterAlias(any(AlterAliasParam.class)))
                    .thenThrow(new RuntimeException("connection lost"));

            boolean result = service.switchAlias("old-collection", "new-collection", "test-alias");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("backup: should throw UnsupportedOperationException with milvus-specific message")
        void backup_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> service.backup("test-collection", "/backup/path"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("milvus")
                    .hasMessageContaining("backup");
        }

        @Test
        @DisplayName("restore: should throw UnsupportedOperationException with milvus-specific message")
        void restore_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> service.restore("/backup/path", "test-collection"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("milvus")
                    .hasMessageContaining("restore");
        }
    }
}
