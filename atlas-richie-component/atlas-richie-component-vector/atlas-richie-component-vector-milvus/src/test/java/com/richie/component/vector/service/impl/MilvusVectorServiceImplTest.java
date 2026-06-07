package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.MilvusConfig;
import com.richie.component.vector.config.VectorProperties;
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
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

        service = new MilvusVectorServiceImpl(vectorStore, embeddingModel, milvusConfig, milvusClient);
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
            KeyValuePair stat = KeyValuePair.newBuilder()
                    .setKey("row_count")
                    .setValue("42")
                    .build();

            GetCollectionStatisticsResponse response = mock(GetCollectionStatisticsResponse.class);
            when(response.getStatsList()).thenReturn(List.of(stat));

            R<GetCollectionStatisticsResponse> successResp = mock(R.class);
            when(successResp.getStatus()).thenReturn(R.Status.Success.getCode());
            when(successResp.getData()).thenReturn(response);
            when(milvusClient.getCollectionStatistics(any(GetCollectionStatisticsParam.class))).thenReturn(successResp);

            long count = service.countDocuments("test-collection");

            assertThat(count).isEqualTo(42L);
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
        @DisplayName("should throw UnsupportedOperationException")
        void listDocumentsHandler_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> service.listDocuments("test", 0, 10))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessage("Milvus listDocuments未实现");
        }
    }

    @Nested
    @DisplayName("searchByVector")
    class SearchByVectorTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is null")
        void searchByVector_withNullVector_shouldThrow() {
            assertThatThrownBy(() -> service.searchByVector("test", null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is empty")
        void searchByVector_withEmptyVector_shouldThrow() {
            assertThatThrownBy(() -> service.searchByVector("test", new float[]{}, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit <= 0")
        void searchByVector_withInvalidLimit_shouldThrow() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> service.searchByVector("test", vector, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should throw RuntimeException when search fails")
        void searchByVector_whenSearchFails_shouldThrow() {
            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};

            R<SearchResults> failResp = mock(R.class);
            when(failResp.getStatus()).thenReturn(-1);
            when(failResp.getMessage()).thenReturn("Search failed");
            when(milvusClient.search(any(SearchParam.class))).thenReturn(failResp);

            assertThatThrownBy(() -> service.searchByVector("test", queryVector, 10))
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
}
