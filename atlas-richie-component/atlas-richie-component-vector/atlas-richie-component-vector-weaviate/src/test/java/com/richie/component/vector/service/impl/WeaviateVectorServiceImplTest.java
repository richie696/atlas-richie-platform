/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import io.weaviate.client.WeaviateClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import io.weaviate.client.base.Result;
import io.weaviate.client.base.WeaviateError;
import io.weaviate.client.base.WeaviateErrorMessage;
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.Raw;
import io.weaviate.client.v1.schema.Schema;
import io.weaviate.client.v1.schema.api.ClassCreator;
import io.weaviate.client.v1.schema.api.ClassExists;
import io.weaviate.client.v1.schema.api.ClassGetter;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import io.weaviate.client.v1.graphql.GraphQL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WeaviateVectorServiceImplTest {

    private VectorStore vectorStore;
    private EmbeddingModel embeddingModel;
    private WeaviateClient weaviateClient;
    private Schema schema;
    private ClassCreator classCreator;
    private ClassExists classExists;
    private ClassGetter classGetter;
    private GraphQL graphQL;
    private Raw raw;

    private WeaviateVectorServiceImpl vectorService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        embeddingModel = mock(EmbeddingModel.class);
        weaviateClient = mock(WeaviateClient.class);
        schema = mock(Schema.class);
        classCreator = mock(ClassCreator.class);
        classExists = mock(ClassExists.class);
        classGetter = mock(ClassGetter.class);
        graphQL = mock(GraphQL.class);
        raw = mock(Raw.class);

        vectorService = new WeaviateVectorServiceImpl(vectorStore, embeddingModel, weaviateClient);
    }

    private WeaviateError createError(String message) {
        return new WeaviateError(500, List.of(new WeaviateErrorMessage(message, null)));
    }

    @SuppressWarnings("unchecked")
    private Result<GraphQLResponse> mockGraphQLResult(GraphQLResponse response, boolean hasErrors, WeaviateError error) {
        Result<GraphQLResponse> result = mock(Result.class);
        when(result.getResult()).thenReturn(response);
        when(result.hasErrors()).thenReturn(hasErrors);
        when(result.getError()).thenReturn(error);
        doReturn(result).when(raw).run();
        return result;
    }

    private void stubGraphQL(GraphQLResponse response) {
        when(weaviateClient.graphQL()).thenReturn(graphQL);
        when(graphQL.raw()).thenReturn(raw);
        when(raw.withQuery(any(String.class))).thenReturn(raw);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("should create WeaviateClass with HNSW config and default values")
        void createIndex_shouldBuildWeaviateClassWithHnswConfig() {
            String indexName = "test_class";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classCreator()).thenReturn(classCreator);
            when(classCreator.withClass(any(WeaviateClass.class))).thenReturn(classCreator);
            Result<Boolean> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(true);
            when(okResult.hasErrors()).thenReturn(false);
            when(classCreator.run()).thenReturn(okResult);

            vectorService.createIndex(indexName, config);

            ArgumentCaptor<WeaviateClass> captor = ArgumentCaptor.forClass(WeaviateClass.class);
            verify(classCreator).withClass(captor.capture());
            WeaviateClass captured = captor.getValue();

            assertThat(captured.getClassName()).isEqualTo(indexName);
            assertThat(captured.getVectorIndexType()).isEqualTo("hnsw");
            assertThat(captured.getVectorizer()).isEqualTo("none");
            assertThat(captured.getVectorIndexConfig()).isNotNull();
            assertThat(captured.getVectorIndexConfig().getDistance()).isEqualTo("cosine");
            assertThat(captured.getReplicationConfig()).isNotNull();
            assertThat(captured.getReplicationConfig().getFactor()).isEqualTo(1);
        }

        @Test
        @DisplayName("should create WeaviateClass with euclidean metric when configured")
        void createIndex_shouldUseConfiguredMetric() {
            String indexName = "custom_class";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setMetric("euclidean");
            config.setReplicas(2);

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classCreator()).thenReturn(classCreator);
            when(classCreator.withClass(any(WeaviateClass.class))).thenReturn(classCreator);
            Result<Boolean> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(true);
            when(okResult.hasErrors()).thenReturn(false);
            when(classCreator.run()).thenReturn(okResult);

            vectorService.createIndex(indexName, config);

            ArgumentCaptor<WeaviateClass> captor = ArgumentCaptor.forClass(WeaviateClass.class);
            verify(classCreator).withClass(captor.capture());
            WeaviateClass captured = captor.getValue();

            assertThat(captured.getVectorIndexConfig().getDistance()).isEqualTo("euclidean");
            assertThat(captured.getReplicationConfig().getFactor()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw RuntimeException when Weaviate returns errors")
        void createIndex_whenWeaviateErrors_shouldThrowRuntimeException() {
            String indexName = "error_class";
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classCreator()).thenReturn(classCreator);
            when(classCreator.withClass(any(WeaviateClass.class))).thenReturn(classCreator);
            Result<Boolean> errResult = mock(Result.class);
            when(errResult.getResult()).thenReturn(null);
            when(errResult.hasErrors()).thenReturn(true);
            when(errResult.getError()).thenReturn(createError("creation failed"));
            when(classCreator.run()).thenReturn(errResult);

            assertThatThrownBy(() -> vectorService.createIndex(indexName, config))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Weaviate createIndex failed");
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("should return true when schema exists returns true")
        void indexExists_whenClassExists_shouldReturnTrue() {
            String indexName = "existing_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.exists()).thenReturn(classExists);
            when(classExists.withClassName(indexName)).thenReturn(classExists);
            Result<Boolean> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(true);
            when(okResult.hasErrors()).thenReturn(false);
            when(classExists.run()).thenReturn(okResult);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should return false when schema exists returns false")
        void indexExists_whenClassNotExists_shouldReturnFalse() {
            String indexName = "missing_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.exists()).thenReturn(classExists);
            when(classExists.withClassName(indexName)).thenReturn(classExists);
            Result<Boolean> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(false);
            when(okResult.hasErrors()).thenReturn(false);
            when(classExists.run()).thenReturn(okResult);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should return false when result is null")
        void indexExists_whenResultNull_shouldReturnFalse() {
            String indexName = "null_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.exists()).thenReturn(classExists);
            when(classExists.withClassName(indexName)).thenReturn(classExists);
            Result<Boolean> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(null);
            when(okResult.hasErrors()).thenReturn(false);
            when(classExists.run()).thenReturn(okResult);

            boolean exists = vectorService.indexExists(indexName);

            assertThat(exists).isFalse();
        }

        @Test
        @DisplayName("should throw RuntimeException when Weaviate returns errors")
        void indexExists_whenWeaviateErrors_shouldThrowRuntimeException() {
            String indexName = "error_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.exists()).thenReturn(classExists);
            when(classExists.withClassName(indexName)).thenReturn(classExists);
            Result<Boolean> errResult = mock(Result.class);
            when(errResult.getResult()).thenReturn(null);
            when(errResult.hasErrors()).thenReturn(true);
            when(errResult.getError()).thenReturn(createError("exists check failed"));
            when(classExists.run()).thenReturn(errResult);

            assertThatThrownBy(() -> vectorService.indexExists(indexName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Weaviate indexExists failed");
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("should return IndexConfig when class exists")
        void getIndexConfig_whenClassExists_shouldReturnConfig() {
            String indexName = "test_class";

            WeaviateClass mockClass = mock(WeaviateClass.class);
            when(mockClass.getClassName()).thenReturn(indexName);
            when(mockClass.getVectorIndexType()).thenReturn("hnsw");
            io.weaviate.client.v1.misc.model.VectorIndexConfig indexConfig =
                    mock(io.weaviate.client.v1.misc.model.VectorIndexConfig.class);
            when(indexConfig.getDistance()).thenReturn("cosine");
            when(mockClass.getVectorIndexConfig()).thenReturn(indexConfig);
            io.weaviate.client.v1.misc.model.ReplicationConfig replConfig =
                    mock(io.weaviate.client.v1.misc.model.ReplicationConfig.class);
            when(replConfig.getFactor()).thenReturn(2);
            when(mockClass.getReplicationConfig()).thenReturn(replConfig);

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classGetter()).thenReturn(classGetter);
            when(classGetter.withClassName(indexName)).thenReturn(classGetter);
            Result<WeaviateClass> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(mockClass);
            when(okResult.hasErrors()).thenReturn(false);
            when(classGetter.run()).thenReturn(okResult);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNotNull();
            assertThat(config.getName()).isEqualTo(indexName);
            assertThat(config.getIndexType()).isEqualTo("hnsw");
            assertThat(config.getMetric()).isEqualTo("cosine");
            assertThat(config.getReplicas()).isEqualTo(2);
        }

        @Test
        @DisplayName("should return null when class not found")
        void getIndexConfig_whenClassNotFound_shouldReturnNull() {
            String indexName = "missing_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classGetter()).thenReturn(classGetter);
            when(classGetter.withClassName(indexName)).thenReturn(classGetter);
            Result<WeaviateClass> okResult = mock(Result.class);
            when(okResult.getResult()).thenReturn(null);
            when(okResult.hasErrors()).thenReturn(false);
            when(classGetter.run()).thenReturn(okResult);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNull();
        }

        @Test
        @DisplayName("should return null when Weaviate returns errors")
        void getIndexConfig_whenErrors_shouldReturnNull() {
            String indexName = "error_class";

            when(weaviateClient.schema()).thenReturn(schema);
            when(schema.classGetter()).thenReturn(classGetter);
            when(classGetter.withClassName(indexName)).thenReturn(classGetter);
            Result<WeaviateClass> errResult = mock(Result.class);
            when(errResult.getResult()).thenReturn(null);
            when(errResult.hasErrors()).thenReturn(true);
            when(errResult.getError()).thenReturn(createError("get failed"));
            when(classGetter.run()).thenReturn(errResult);

            VectorProperties.IndexConfig config = vectorService.getIndexConfig(indexName);

            assertThat(config).isNull();
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("should return document count from GraphQL query")
        void countDocuments_shouldQueryAndCount() {
            String indexName = "test_class";

            Map<String, Object> doc1 = Map.of("id", "doc-1");
            Map<String, Object> doc2 = Map.of("id", "doc-2");
            List<Object> items = List.of(doc1, doc2);

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, items);
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("should return 0 when Get is null")
        void countDocuments_whenGetNull_shouldReturnZero() {
            String indexName = "empty_class";

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Get", null);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should return 0 when data is null")
        void countDocuments_whenDataNull_shouldReturnZero() {
            String indexName = "null_class";

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(null);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            long count = vectorService.countDocuments(indexName);

            assertThat(count).isZero();
        }

        @Test
        @DisplayName("should throw RuntimeException when GraphQL returns errors")
        void countDocuments_whenGraphQLErrors_shouldThrowRuntimeException() {
            String indexName = "error_class";

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(null);

            stubGraphQL(response);
            mockGraphQLResult(response, true, createError("query failed"));

            assertThatThrownBy(() -> vectorService.countDocuments(indexName))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Weaviate countDocuments failed");
        }
    }

    @Nested
    @DisplayName("listDocumentsHandler")
    class ListDocumentsHandlerTests {

        @Test
        @DisplayName("should return documents with id and content from GraphQL")
        void listDocumentsHandler_shouldParseDocuments() {
            String indexName = "test_class";

            Map<String, Object> additional1 = Map.of("id", "doc-1");
            Map<String, Object> item1 = Map.of("content", "Hello world", "_additional", additional1);

            Map<String, Object> additional2 = Map.of("id", "doc-2");
            Map<String, Object> item2 = Map.of("content", "Another document", "_additional", additional2);

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of(item1, item2));
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorDocument> docs = vectorService.listDocuments(indexName, 0, 10);

            assertThat(docs).hasSize(2);
            assertThat(docs.get(0).getId()).isEqualTo("doc-1");
            assertThat(docs.get(0).getContent()).isEqualTo("Hello world");
            assertThat(docs.get(1).getId()).isEqualTo("doc-2");
            assertThat(docs.get(1).getContent()).isEqualTo("Another document");
        }

        @Test
        @DisplayName("should return empty list when no items")
        void listDocumentsHandler_whenNoItems_shouldReturnEmpty() {
            String indexName = "empty_class";

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of());
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorDocument> docs = vectorService.listDocuments(indexName, 0, 10);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when Get is null")
        void listDocumentsHandler_whenGetNull_shouldReturnEmpty() {
            String indexName = "null_class";

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Get", null);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorDocument> docs = vectorService.listDocuments(indexName, 0, 10);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should skip items without _additional")
        void listDocumentsHandler_shouldSkipItemsWithoutAdditional() {
            String indexName = "partial_class";

            Map<String, Object> validItem = new HashMap<>();
            validItem.put("content", "Valid content");
            validItem.put("_additional", Map.of("id", "doc-1"));

            Map<String, Object> invalidItem = new HashMap<>();
            invalidItem.put("content", "No additional");

            Map<String, Object> nullAdditionalItem = new HashMap<>();
            nullAdditionalItem.put("content", "Null additional");
            nullAdditionalItem.put("_additional", null);

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of(invalidItem, validItem, nullAdditionalItem));
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorDocument> docs = vectorService.listDocuments(indexName, 0, 10);

            assertThat(docs).hasSize(1);
            assertThat(docs.get(0).getId()).isEqualTo("doc-1");
        }

        @Test
        @DisplayName("should throw RuntimeException when GraphQL returns errors")
        void listDocumentsHandler_whenGraphQLErrors_shouldThrowRuntimeException() {
            String indexName = "error_class";

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(null);

            stubGraphQL(response);
            mockGraphQLResult(response, true, createError("list failed"));

            assertThatThrownBy(() -> vectorService.listDocuments(indexName, 0, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Weaviate listDocumentsHandler failed");
        }
    }

    @Nested
    @DisplayName("searchByVector")
    class SearchByVectorTests {

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is null")
        void searchByVector_withNullVector_shouldThrowException() {
            assertThatThrownBy(() -> vectorService.searchByVector("idx", null, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when vector is empty")
        void searchByVector_withEmptyVector_shouldThrowException() {
            float[] emptyVector = new float[0];
            assertThatThrownBy(() -> vectorService.searchByVector("idx", emptyVector, 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("查询向量不能为空");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is zero")
        void searchByVector_withZeroLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.searchByVector("idx", vector, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when limit is negative")
        void searchByVector_withNegativeLimit_shouldThrowException() {
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};
            assertThatThrownBy(() -> vectorService.searchByVector("idx", vector, -5))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("limit 必须大于 0");
        }

        @Test
        @DisplayName("should return results from nearVector GraphQL query")
        void searchByVector_withValidParams_shouldReturnResults() {
            String indexName = "test_class";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            Map<String, Object> additional1 = Map.of(
                    "id", "doc-1",
                    "certainty", 0.95
            );
            Map<String, Object> item1 = Map.of(
                    "content", "Matching content",
                    "_additional", additional1
            );

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of(item1));
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 5);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("doc-1");
            assertThat(results.get(0).getContent()).isEqualTo("Matching content");
            assertThat(results.get(0).getScore()).isEqualTo(0.95);
        }

        @Test
        @DisplayName("should return empty list when no results")
        void searchByVector_whenNoResults_shouldReturnEmpty() {
            String indexName = "empty_class";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of());
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 10);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return empty list when Get is null")
        void searchByVector_whenGetNull_shouldReturnEmpty() {
            String indexName = "null_class";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Get", null);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 10);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should skip items without id even if content exists")
        void searchByVector_shouldSkipItemsWithoutId() {
            String indexName = "partial_class";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            Map<String, Object> additional1 = Map.of("certainty", 0.9);
            Map<String, Object> item1 = Map.of("content", "No id", "_additional", additional1);

            Map<String, Object> additional2 = Map.of("id", "doc-2", "certainty", 0.8);
            Map<String, Object> item2 = Map.of("content", "Has id", "_additional", additional2);

            Map<String, Object> getMap = new HashMap<>();
            getMap.put(indexName, List.of(item1, item2));
            Map<String, Object> dataMap = Map.of("Get", getMap);

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            List<VectorSearchResult> results = vectorService.searchByVector(indexName, vector, 10);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo("doc-2");
        }

        @Test
        @DisplayName("should throw RuntimeException when GraphQL returns errors")
        void searchByVector_whenGraphQLErrors_shouldThrowRuntimeException() {
            String indexName = "error_class";
            float[] vector = new float[]{0.1f, 0.2f, 0.3f};

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(null);

            stubGraphQL(response);
            mockGraphQLResult(response, true, createError("search failed"));

            assertThatThrownBy(() -> vectorService.searchByVector(indexName, vector, 10))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Weaviate searchByVector failed");
        }
    }

    @Nested
    @DisplayName("vectorToString")
    class VectorToStringTests {

        @Test
        @DisplayName("should convert vector to bracket string format via searchByVector")
        void vectorToString_shouldFormatCorrectly() {
            String indexName = "test_class";
            float[] vector = new float[]{0.1f, -0.2f, 0.3f};

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Get", Map.of(indexName, List.of()));

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            verify(raw, never()).withQuery(queryCaptor.capture());

            vectorService.searchByVector(indexName, vector, 5);

            verify(raw).withQuery(queryCaptor.capture());
            String graphqlQuery = queryCaptor.getValue();
            assertThat(graphqlQuery).contains("[0.1,-0.2,0.3]");
        }

        @Test
        @DisplayName("should handle single element vector")
        void vectorToString_singleElement() {
            String indexName = "test_class";
            float[] vector = new float[]{1.0f};

            Map<String, Object> dataMap = new HashMap<>();
            dataMap.put("Get", Map.of(indexName, List.of()));

            GraphQLResponse response = mock(GraphQLResponse.class);
            when(response.getData()).thenReturn(dataMap);

            stubGraphQL(response);
            mockGraphQLResult(response, false, null);

            ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
            vectorService.searchByVector(indexName, vector, 5);

            verify(raw).withQuery(queryCaptor.capture());
            String graphqlQuery = queryCaptor.getValue();
            assertThat(graphqlQuery).contains("[1.0]");
        }
    }

    @Nested
    @DisplayName("Base class operations")
    class BaseClassOperationTests {

        @Test
        @DisplayName("addDocument should delegate to vectorStore")
        void addDocument_shouldDelegateToVectorStore() {
            VectorDocument doc = new VectorDocument();
            doc.setContent("test content");
            doc.setMetadata(new HashMap<>());

            vectorService.addDocument(doc);

            verify(vectorStore).add(any());
        }

        @Test
        @DisplayName("deleteDocument should delegate to vectorStore")
        void deleteDocument_shouldDelegateToVectorStore() {
            vectorService.deleteDocument("doc-1");

            verify(vectorStore).delete("doc-1");
        }

        @Test
        @DisplayName("searchByText should delegate to vectorStore")
        void searchByText_shouldDelegateToVectorStore() {
            when(vectorStore.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                    .thenReturn(List.of());

            vectorService.searchByText("test query", 10);

            verify(vectorStore).similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class));
        }
    }
}
