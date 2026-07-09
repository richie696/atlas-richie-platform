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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PostgresqlVectorServiceImpl}.
 *
 * <p>These tests verify PostgreSQL-specific vector operations including
 * index existence checks, document counting, pagination queries, and
 * L2 distance-based similarity search. All database interactions are
 * mocked to ensure fast, isolated unit tests.</p>
 *
 * <p><b>Design note:</b> The createIndex and deleteIndex methods are
 * unsupported by design (pgvector index creation requires careful
 * consideration of index type, table structure, etc). These tests
 * verify the methods log appropriately rather than throwing.</p>
 */
@ExtendWith(MockitoExtension.class)
class PostgresqlVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PostgresqlVectorServiceImpl postgresqlVectorService;

    @BeforeEach
    void setUp() {
        // PostgresqlVectorServiceImpl requires VectorStore, EmbeddingModel, and JdbcTemplate
        postgresqlVectorService = new PostgresqlVectorServiceImpl(vectorStore, embeddingModel, jdbcTemplate);
    }

    @Nested
    @DisplayName("createIndex")
    class CreateIndexTests {

        @Test
        @DisplayName("should log info instead of creating index")
        void createIndex_shouldLogInfoInsteadOfCreating() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("test_index");
            config.setDimension(1536);

            // Should not throw, just log
            postgresqlVectorService.createIndex("test_index", config);

            // Verify no database interaction occurred
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("deleteIndex")
    class DeleteIndexTests {

        @Test
        @DisplayName("should log info instead of deleting index")
        void deleteIndex_shouldLogInfoInsteadOfDeleting() {
            // Should not throw, just log
            postgresqlVectorService.deleteIndex("test_index");

            // Verify no database interaction occurred
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("indexExists")
    class IndexExistsTests {

        @Test
        @DisplayName("should return true when table exists")
        void indexExists_whenTableExists_shouldReturnTrue() {
            // Mock the information_schema query - table exists
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_documents")
            )).thenReturn(true);

            boolean result = postgresqlVectorService.indexExists("documents");

            assertThat(result).isTrue();
            verify(jdbcTemplate).queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_documents")
            );
        }

        @Test
        @DisplayName("should return false when table does not exist")
        void indexExists_whenTableDoesNotExist_shouldReturnFalse() {
            // Mock the information_schema query - table does not exist
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_nonexistent")
            )).thenReturn(false);

            boolean result = postgresqlVectorService.indexExists("nonexistent");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("should return false when query returns null")
        void indexExists_whenQueryReturnsNull_shouldReturnFalse() {
            // Mock returns null (edge case)
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_nullcase")
            )).thenReturn(null);

            boolean result = postgresqlVectorService.indexExists("nullcase");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("getIndexConfig")
    class GetIndexConfigTests {

        @Test
        @DisplayName("should return index config with correct dimension")
        void getIndexConfig_shouldReturnConfigWithDimension() {
            // First, indexExists is called to check if index exists
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_documents")
            )).thenReturn(true);

            // Mock the pg_attribute query for vector column dimension
            when(jdbcTemplate.queryForObject(
                    eq("SELECT attndims FROM pg_attribute WHERE attrelid = ?::regclass AND attname = 'vector'"),
                    eq(Integer.class),
                    eq("vector_documents")
            )).thenReturn(1536);

            VectorProperties.IndexConfig config = postgresqlVectorService.getIndexConfig("documents");

            assertThat(config.getName()).isEqualTo("documents");
            assertThat(config.getDimension()).isEqualTo(1536);
            assertThat(config.getMetric()).isEqualTo("l2");
        }

        @Test
        @DisplayName("should throw UnsupportedOperationException when index does not exist")
        void getIndexConfig_whenIndexDoesNotExist_shouldThrow() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_nonexistent")
            )).thenReturn(false);

            assertThatThrownBy(() -> postgresqlVectorService.getIndexConfig("nonexistent"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("索引不存在");
        }

        @Test
        @DisplayName("should return dimension 0 when attndims is null")
        void getIndexConfig_whenAttndimsIsNull_shouldReturnZeroDimension() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_documents")
            )).thenReturn(true);

            when(jdbcTemplate.queryForObject(
                    eq("SELECT attndims FROM pg_attribute WHERE attrelid = ?::regclass AND attname = 'vector'"),
                    eq(Integer.class),
                    eq("vector_documents")
            )).thenReturn(null);

            VectorProperties.IndexConfig config = postgresqlVectorService.getIndexConfig("documents");

            assertThat(config.getDimension()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("countDocuments")
    class CountDocumentsTests {

        @Test
        @DisplayName("should return correct document count")
        void countDocuments_shouldReturnCount() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_documents"),
                    eq(Long.class)
            )).thenReturn(42L);

            long count = postgresqlVectorService.countDocuments("documents");

            assertThat(count).isEqualTo(42L);
        }

        @Test
        @DisplayName("should return 0 when table is empty")
        void countDocuments_whenTableEmpty_shouldReturnZero() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_documents"),
                    eq(Long.class)
            )).thenReturn(0L);

            long count = postgresqlVectorService.countDocuments("documents");

            assertThat(count).isEqualTo(0L);
        }

        @Test
        @DisplayName("should return 0 when query throws exception")
        void countDocuments_whenQueryThrows_shouldReturnZero() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_nonexistent"),
                    eq(Long.class)
            )).thenThrow(new RuntimeException("Table does not exist"));

            long count = postgresqlVectorService.countDocuments("nonexistent");

            // Returns 0 on exception rather than propagating
            assertThat(count).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("listDocumentsHandler")
    class ListDocumentsHandlerTests {

        @Test
        @DisplayName("should return empty list when no documents")
        void listDocumentsHandler_whenNoDocuments_shouldReturnEmptyList() {
            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of());

            List<VectorDocument> docs = postgresqlVectorService.listDocumentsHandler("documents", 0, 10);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should return documents with correctly converted vectors")
        void listDocumentsHandler_shouldConvertVectorsAndReturnDocuments() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("metadata", Map.of("author", "test"));

            List<Double> vectorList = Arrays.asList(0.1, 0.2, 0.3);
            row.put("vector", vectorList);

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorDocument> docs = postgresqlVectorService.listDocumentsHandler("documents", 0, 10);

            assertThat(docs).hasSize(1);
            VectorDocument doc = docs.getFirst();
            assertThat(doc.getId()).isEqualTo("doc-1");
            assertThat(doc.getMetadata()).containsEntry("author", "test");

            assertThat(doc.getVector()).hasSize(3);
            assertThat(doc.getVector()[0]).isEqualTo(0.1f);
            assertThat(doc.getVector()[1]).isEqualTo(0.2f);
            assertThat(doc.getVector()[2]).isEqualTo(0.3f);
        }

        @Test
        @DisplayName("should handle null metadata")
        void listDocumentsHandler_whenMetadataNull_shouldHandleGracefully() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test content");
            row.put("metadata", null);
            row.put("vector", Arrays.asList(0.1, 0.2));

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorDocument> docs = postgresqlVectorService.listDocumentsHandler("documents", 0, 10);

            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().getMetadata()).isNull();
        }

        @Test
        @DisplayName("should handle non-List vector object")
        void listDocumentsHandler_whenVectorNotList_shouldNotSetVector() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test content");
            row.put("metadata", Map.of());
            row.put("vector", "not-a-list");

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorDocument> docs = postgresqlVectorService.listDocumentsHandler("documents", 0, 10);

            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().getVector()).isNull();
        }

        @Test
        @DisplayName("should handle pagination with offset and limit")
        void listDocumentsHandler_withOffsetAndLimit_shouldQueryCorrectly() {
            when(jdbcTemplate.queryForList(any(String.class), eq(20), eq(15)))
                    .thenReturn(List.of());

            postgresqlVectorService.listDocumentsHandler("documents", 20, 15);

            verify(jdbcTemplate).queryForList(
                    eq("SELECT id, vector, metadata FROM vector_documents ORDER BY id OFFSET ? LIMIT ?"),
                    eq(20),
                    eq(15)
            );
        }
    }

    @Nested
    @DisplayName("searchByVector")
    class SearchByVectorTests {

        @Test
        @DisplayName("should return empty list when no results")
        void searchByVector_whenNoResults_shouldReturnEmptyList() {
            when(jdbcTemplate.queryForList(any(String.class), eq(10)))
                    .thenReturn(List.of());

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 10);

            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("should return results with correct L2 distance to similarity conversion")
        void searchByVector_shouldConvertDistanceToSimilarity() {
            // Prepare mock search result with distance
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test document");
            row.put("distance", 0.5);  // L2 distance of 0.5
            row.put("vector", Arrays.asList(0.1, 0.2));
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 5);

            assertThat(results).hasSize(1);
            VectorSearchResult result = results.getFirst();
            assertThat(result.getId()).isEqualTo("doc-1");
            assertThat(result.getContent()).isEqualTo("Test document");

            // Verify L2 distance to similarity conversion: score = 1/(1+distance)
            // 0.5 distance -> 1/(1+0.5) = 0.666...
            assertThat(result.getScore()).isEqualTo(1.0 / (1.0 + 0.5));
            assertThat(result.getScore()).isCloseTo(0.666, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("should handle zero distance for exact match")
        void searchByVector_withZeroDistance_shouldReturnHighScore() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Exact match");
            row.put("distance", 0.0);  // Exact match, zero distance
            row.put("vector", Arrays.asList(0.1, 0.2));
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 5);

            // score = 1/(1+0) = 1.0
            assertThat(results.getFirst().getScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should handle null distance")
        void searchByVector_whenDistanceNull_shouldReturnNullScore() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test");
            row.put("distance", null);
            row.put("vector", null);
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 5);

            assertThat(results.getFirst().getScore()).isNull();
        }

        @Test
        @DisplayName("should handle vector as List and convert to float array")
        void searchByVector_whenVectorIsList_shouldConvertToFloatArray() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test");
            row.put("distance", 0.3);
            row.put("vector", Arrays.asList(0.1, 0.2, 0.3, 0.4));
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 5);

            float[] resultVector = results.getFirst().getVector();
            assertThat(resultVector).hasSize(4);
            assertThat(resultVector[0]).isEqualTo(0.1f);
            assertThat(resultVector[3]).isEqualTo(0.4f);
        }

        @Test
        @DisplayName("should handle non-List vector object")
        void searchByVector_whenVectorNotList_shouldKeepNull() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test");
            row.put("distance", 0.3);
            row.put("vector", "unexpected-type");
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<VectorSearchResult> results = postgresqlVectorService.searchByVector("documents", new float[]{0.1f, 0.2f}, 5);

            assertThat(results.getFirst().getVector()).isNull();
        }

        @Test
        @DisplayName("should construct correct SQL with vector literal")
        void searchByVector_shouldConstructCorrectSqlWithVectorLiteral() {
            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of());

            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};
            postgresqlVectorService.searchByVector("documents", queryVector, 5);

            // Verify SQL contains properly formatted vector literal [0.1,0.2,0.3]
            verify(jdbcTemplate).queryForList(
                    contains("SELECT id, content, metadata, vector, vector <-> '[0.1,0.2,0.3]'::vector as distance FROM vector_documents"),
                    eq(5)
            );
        }
    }

    @Nested
    @DisplayName("listDocuments (inherited behavior)")
    class ListDocumentsInheritedTests {

        @Test
        @DisplayName("should cap limit at MAX_LIST_LIMIT")
        void listDocuments_shouldCapLimitAtMax() {
            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(1000)))
                    .thenReturn(List.of());

            // Request more than MAX_LIST_LIMIT (1000)
            postgresqlVectorService.listDocuments("documents", 0, 5000);

            verify(jdbcTemplate).queryForList(
                    any(String.class),
                    eq(0),
                    eq(1000)  // Should be capped at 1000
            );
        }

        @Test
        @DisplayName("should return empty list when limit is zero")
        void listDocuments_withZeroLimit_shouldReturnEmpty() {
            List<VectorDocument> docs = postgresqlVectorService.listDocuments("documents", 0, 0);
            assertThat(docs).isEmpty();
            verifyNoInteractions(jdbcTemplate);
        }
    }
}
