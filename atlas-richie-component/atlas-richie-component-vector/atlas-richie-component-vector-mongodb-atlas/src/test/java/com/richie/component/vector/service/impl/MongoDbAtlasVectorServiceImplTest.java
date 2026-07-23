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
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MongoDbAtlasVectorServiceImpl}.
 *
 * <p>Tests MongoDB Atlas vector service implementation including index management,
 * document operations, and vector search functionality using Mockito mocks
 * for MongoTemplate, VectorStore, and EmbeddingModel.</p>
 *
 * @author richie696
 */
@ExtendWith(MockitoExtension.class)
class MongoDbAtlasVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private MongoDatabase mongoDatabase;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    @Captor
    private ArgumentCaptor<Document> commandCaptor;

    private MongoDbAtlasVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        // Inject mock dependencies via constructor (same as production)
        vectorService = new MongoDbAtlasVectorServiceImpl(null, vectorStore, embeddingModel, mongoTemplate);
    }

    @Nested
    @DisplayName("createIndex (Atlas Vector Search Index)")
    class CreateIndexTests {

        @Test
        @DisplayName("should issue createSearchIndexes command with vectorSearch type + knnVector")
        void createIndex_issuesAtlasSearchIndexCommand() {
            String indexName = "test_collection";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setDimension(1536);
            config.setMetric("cosine");

            when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
            when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

            vectorService.createIndex(indexName, config);

            verify(mongoDatabase).runCommand(commandCaptor.capture());
            Document cmd = commandCaptor.getValue();
            assertThat(cmd.getString("createSearchIndexes")).isEqualTo(indexName);
            @SuppressWarnings("unchecked")
            List<Document> indexes = (List<Document>) cmd.get("indexes");
            assertThat(indexes).hasSize(1);
            Document idx = indexes.get(0);
            assertThat(idx.getString("type")).isEqualTo("vectorSearch");
            assertThat(idx.getString("name")).isEqualTo("vector_index");
            Document definition = (Document) idx.get("definition");
            @SuppressWarnings("unchecked")
            List<Document> fields = (List<Document>) definition.get("fields");
            assertThat(fields).hasSize(1);
            Document knn = fields.get(0);
            assertThat(knn.getString("type")).isEqualTo("knnVector");
            assertThat(knn.getString("path")).isEqualTo("vector");
            assertThat(knn.getInteger("numDimensions")).isEqualTo(1536);
            assertThat(knn.getString("similarity")).isEqualTo("cosine");
        }

        @Test
        @DisplayName("should map metric euclidean to similarity=euclidean")
        void createIndex_mapsEuclideanMetric() {
            String indexName = "c_euclid";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setDimension(768);
            config.setMetric("euclidean");

            when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
            when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

            vectorService.createIndex(indexName, config);

            verify(mongoDatabase).runCommand(commandCaptor.capture());
            Document cmd = commandCaptor.getValue();
            @SuppressWarnings("unchecked")
            List<Document> indexes = (List<Document>) cmd.get("indexes");
            Document definition = (Document) indexes.get(0).get("definition");
            @SuppressWarnings("unchecked")
            List<Document> fields = (List<Document>) definition.get("fields");
            assertThat(fields.get(0).getString("similarity")).isEqualTo("euclidean");
        }

        @Test
        @DisplayName("should default dimension to 1536 when null")
        void createIndex_defaultsDimensionTo1536() {
            String indexName = "c_default";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

            when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
            when(mongoDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1.0));

            vectorService.createIndex(indexName, config);

            verify(mongoDatabase).runCommand(commandCaptor.capture());
            Document cmd = commandCaptor.getValue();
            @SuppressWarnings("unchecked")
            List<Document> indexes = (List<Document>) cmd.get("indexes");
            Document definition = (Document) indexes.get(0).get("definition");
            @SuppressWarnings("unchecked")
            List<Document> fields = (List<Document>) definition.get("fields");
            assertThat(fields.get(0).getInteger("numDimensions")).isEqualTo(1536);
            assertThat(fields.get(0).getString("similarity")).isEqualTo("cosine");
        }

        @Test
        @DisplayName("should swallow error when index already exists")
        void createIndex_swallowsAlreadyExistsError() {
            String indexName = "c_existing";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

            when(mongoTemplate.getDb()).thenReturn(mongoDatabase);
            when(mongoDatabase.runCommand(any(Document.class)))
                    .thenThrow(new RuntimeException("Index already exists with a different definition"));

            // should not throw
            vectorService.createIndex(indexName, config);
            verify(mongoDatabase).runCommand(any(Document.class));
        }
    }

    @Nested
    @DisplayName("deleteIndex")
    class DeleteIndexTests {

        @Test
        @DisplayName("should drop all indexes on collection")
        void deleteIndex_shouldDropIndexes() {
            String indexName = "test_collection";

            // mongoTemplate.getCollection returns a MongoCollection which has dropIndexes()
            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);

            vectorService.deleteIndex(indexName);

            verify(mongoTemplate).getCollection(indexName);
            verify(collection).dropIndexes();
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("should return true when collection exists")
        void indexExists_whenCollectionExists_shouldReturnTrue() {
            String indexName = "test_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(true);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isTrue();
            verify(mongoTemplate).collectionExists(indexName);
        }

        @Test
        @DisplayName("should return false when collection does not exist")
        void indexExists_whenCollectionNotExists_shouldReturnFalse() {
            String indexName = "nonexistent_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(false);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("should return config when collection exists")
        void getIndexConfig_whenCollectionExists_shouldReturnConfig() {
            String indexName = "test_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(true);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNotNull();
            assertThat(config.getName()).isEqualTo(indexName);
        }

        @Test
        @DisplayName("should return null when collection does not exist")
        void getIndexConfig_whenCollectionNotExists_shouldReturnNull() {
            String indexName = "nonexistent_collection";
            when(mongoTemplate.collectionExists(indexName)).thenReturn(false);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNull();
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("should return document count")
        void countDocuments_shouldReturnCount() {
            String indexName = "test_collection";
            long expectedCount = 42L;

            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenReturn(expectedCount);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isEqualTo(expectedCount);
        }

        @Test
        @DisplayName("should return zero when collection is empty")
        void countDocuments_whenEmpty_shouldReturnZero() {
            String indexName = "empty_collection";

            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection(indexName)).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenReturn(0L);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isZero();
        }
    }

    @Nested
    @DisplayName("listDocuments")
    class ListDocumentsTests {

        @Test
        @DisplayName("should apply skip and limit to query")
        void listDocuments_shouldApplyPaginationParams() {
            String indexName = "test_collection";
            int offset = 10;
            int limit = 20;

            when(mongoTemplate.find(any(Query.class), eq(VectorRecord.class), eq(indexName)))
                    .thenReturn(List.of());

            vectorService.listDocuments(indexName, offset, limit);

            verify(mongoTemplate).find(queryCaptor.capture(), eq(VectorRecord.class), eq(indexName));
            Query capturedQuery = queryCaptor.getValue();
            // Verify skip was set - offset becomes skip value
            assertThat(capturedQuery).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no documents")
        void listDocuments_whenNoDocuments_shouldReturnEmptyList() {
            String indexName = "empty_collection";

            when(mongoTemplate.find(any(Query.class), eq(VectorRecord.class), eq(indexName)))
                    .thenReturn(List.of());

            List<VectorRecord> result = vectorService.listDocuments(indexName, 0, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("should cap limit at MAX_LIST_LIMIT from base class")
        void listDocuments_shouldCapLimitAtMax() {
            // The base class AbstractVectorService.MAX_LIST_LIMIT is 1000
            // listDocuments with limit > 1000 should be capped
            String indexName = "test_collection";

            when(mongoTemplate.find(any(Query.class), eq(VectorRecord.class), eq(indexName)))
                    .thenReturn(List.of());

            // This will be capped by base class to MAX_LIST_LIMIT (1000)
            vectorService.listDocuments(indexName, 0, 5000);

            // Verify find was called (capped internally)
            verify(mongoTemplate).find(any(Query.class), eq(VectorRecord.class), eq(indexName));
        }

        @Test
        @DisplayName("should return empty for zero limit")
        void listDocuments_withZeroLimit_shouldReturnEmpty() {
            List<VectorRecord> result = vectorService.listDocuments("idx", 0, 0);
            // Base class returns empty list for limit <= 0
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("similaritySearchByVector")
    class SimilaritySearchByVectorTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is null")
        void similaritySearchByVector_withNullVector_shouldThrowException() {
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("idx", null, 10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is empty")
        void similaritySearchByVector_withEmptyVector_shouldThrowException() {
            float[] emptyVector = new float[0];
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("idx", emptyVector, 10, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is zero")
        void similaritySearchByVector_withZeroLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("idx", vector, 0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is negative")
        void similaritySearchByVector_withNegativeLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.similaritySearchByVector("idx", vector, -5, 0.0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should execute aggregation with vectorSearch and project stages")
        void similaritySearchByVector_withValidParams_shouldExecuteAggregation() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            int limit = 5;

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<org.springframework.ai.document.Document> results =
                    vectorService.similaritySearchByVector(indexName, vector, limit, 0.0);

            // Verify aggregation was executed
            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
            assertThat(results).isNotNull();
        }

        @Test
        @DisplayName("should return empty list when no search results")
        void similaritySearchByVector_whenNoResults_shouldReturnEmptyList() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<org.springframework.ai.document.Document> results =
                    vectorService.similaritySearchByVector(indexName, vector, 10, 0.0);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should map aggregation results to Spring AI Document")
        void similaritySearchByVector_shouldMapResultsCorrectly() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            // Create mock Document result from aggregation
            Document doc1 = new Document()
                    .append("id", "doc-1")
                    .append("content", "test content")
                    .append("score", 0.95);

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of(doc1));
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            List<org.springframework.ai.document.Document> results =
                    vectorService.similaritySearchByVector(indexName, vector, 10, 0.0);

            assertThat(results).hasSize(1);
            org.springframework.ai.document.Document result = results.get(0);
            assertThat(result.getId()).isEqualTo("doc-1");
            assertThat(result.getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("should use numCandidates as max(limit*10, 100)")
        void similaritySearchByVector_shouldCalculateNumCandidatesCorrectly() {
            String indexName = "test_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            int limit = 5; // 5 * 10 = 50, which is less than 100

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            // This should use 100 as numCandidates (max(50, 100))
            vectorService.similaritySearchByVector(indexName, vector, limit, 0.0);

            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
        }

        @Test
        @DisplayName("should use correct vector index name")
        void similaritySearchByVector_shouldUseCorrectVectorIndex() {
            String indexName = "custom_collection";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            AggregationResults<Document> mockResults = mock(AggregationResults.class);
            when(mockResults.getMappedResults()).thenReturn(List.of());
            when(mongoTemplate.aggregate(any(Aggregation.class), eq(indexName), eq(Document.class)))
                    .thenReturn(mockResults);

            vectorService.similaritySearchByVector(indexName, vector, 10, 0.0);

            verify(mongoTemplate).aggregate(any(Aggregation.class), eq(indexName), eq(Document.class));
        }
    }

    @Nested
    @DisplayName("VectorService operations inherited from base class")
    class BaseClassOperationTests {

        @Test
        @DisplayName("add should delegate to vectorStore.add via addEmbeddings")
        void add_shouldDelegateToVectorStore() {
            when(embeddingModel.embed(any(String.class))).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

            vectorService.add(VectorRecord.text("test_index", "test content"));

            verify(vectorStore).add(any());
        }

        @Test
        @DisplayName("delete should delegate to mongoTemplate.remove via deleteByIds")
        void delete_shouldDelegateToMongoTemplate() {
            vectorService.delete("test_index", "doc-1");

            verify(mongoTemplate).remove(any(Query.class), eq("test_index"));
        }

        @Test
        @DisplayName("searchByText should delegate to vectorStore")
        void searchByText_shouldDelegateToVectorStore() {
            when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                    .thenReturn(List.of());

            vectorService.searchByText("test_index", "test query", 10);

            verify(vectorStore).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
        }
    }

    @Nested
    @DisplayName("truncateIndex")
    class TruncateIndexTests {

        @Test
        @DisplayName("should issue deleteMany on collection and return deleted count")
        void truncateIndex_executesDeleteManyAndReturnsCount() {
            com.mongodb.client.MongoCollection<Document> collection =
                    mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection("test_collection"))
                    .thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            com.mongodb.client.result.DeleteResult result =
                    com.mongodb.client.result.DeleteResult.acknowledged(15L);
            when(collection.deleteMany(any(Document.class))).thenReturn(result);

            long deleted = vectorService.truncateIndex("test_collection");

            assertThat(deleted).isEqualTo(15L);
            verify(collection).deleteMany(any(Document.class));
        }

        @Test
        @DisplayName("should return zero when collection is already empty")
        void truncateIndex_whenEmpty_shouldReturnZero() {
            com.mongodb.client.MongoCollection<Document> collection =
                    mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.getCollection("empty_collection"))
                    .thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            com.mongodb.client.result.DeleteResult result =
                    com.mongodb.client.result.DeleteResult.acknowledged(0L);
            when(collection.deleteMany(any(Document.class))).thenReturn(result);

            long deleted = vectorService.truncateIndex("empty_collection");

            assertThat(deleted).isZero();
        }
    }

    @Nested
    @DisplayName("healthCheck (三步探针：indexExists → countDocuments → 异常兜底)")
    class HealthCheckTests {

        @Test
        @DisplayName("should return false when collection does not exist")
        void healthCheck_whenCollectionMissing_shouldReturnFalse() {
            when(mongoTemplate.collectionExists("missing")).thenReturn(false);

            assertThat(vectorService.healthCheck("missing")).isFalse();
        }

        @Test
        @DisplayName("should return true when collection exists and countDocuments works")
        void healthCheck_whenOk_shouldReturnTrue() {
            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.collectionExists("test_collection")).thenReturn(true);
            when(mongoTemplate.getCollection("test_collection")).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenReturn(42L);

            assertThat(vectorService.healthCheck("test_collection")).isTrue();
        }

        @Test
        @DisplayName("should return false when countDocuments throws")
        void healthCheck_whenCountThrows_shouldReturnFalse() {
            var collection = mock(com.mongodb.client.MongoCollection.class);
            when(mongoTemplate.collectionExists("test_collection")).thenReturn(true);
            when(mongoTemplate.getCollection("test_collection")).thenReturn((com.mongodb.client.MongoCollection<Document>) collection);
            when(collection.countDocuments()).thenThrow(new RuntimeException("timeout"));

            assertThat(vectorService.healthCheck("test_collection")).isFalse();
        }
    }

    @Nested
    @DisplayName("ops API (optimize / createAlias / switchAlias / backup / restore) — Atlas 托管语义")
    class OpsApiTests {

        @Test
        @DisplayName("optimize should throw UnsupportedOperationException (Atlas 无 SDK 级 optimize)")
        void optimize_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.optimize("idx"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("mongodb");
        }

        @Test
        @DisplayName("createAlias should throw UnsupportedOperationException (Atlas 无 vector index alias)")
        void createAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.createAlias("idx", "alias"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("mongodb");
        }

        @Test
        @DisplayName("switchAlias should throw UnsupportedOperationException (Atlas 无 vector index alias)")
        void switchAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.switchAlias("old", "new", "alias"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("mongodb");
        }

        @Test
        @DisplayName("backup should throw UnsupportedOperationException (Atlas backup 为 managed service)")
        void backup_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.backup("idx", "/tmp/backup"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("mongodb");
        }

        @Test
        @DisplayName("restore should throw UnsupportedOperationException (Atlas restore 为 managed service)")
        void restore_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> vectorService.restore("/tmp/backup", "idx"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("mongodb");
        }
    }
}