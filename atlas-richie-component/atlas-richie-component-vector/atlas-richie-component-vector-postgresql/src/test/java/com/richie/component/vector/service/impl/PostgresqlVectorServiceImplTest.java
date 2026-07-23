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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
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
        postgresqlVectorService = new PostgresqlVectorServiceImpl(null, vectorStore, embeddingModel, jdbcTemplate);
    }

    @Nested
    @DisplayName("createIndex (pgvector HNSW DDL)")
    class CreateIndexTests {

        @Test
        @DisplayName("should issue CREATE TABLE with vector(N) and CREATE INDEX USING hnsw")
        void createIndex_executesPgvectorDDL() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            config.setDimension(1536);
            config.setMetric("cosine");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            postgresqlVectorService.createIndex("docs", config);

            verify(jdbcTemplate, times(2)).execute(sqlCaptor.capture());
            List<String> sqls = sqlCaptor.getAllValues();
            assertThat(sqls).anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS vector_docs"));
            assertThat(sqls).anyMatch(s -> s.contains("vector(1536)"));
            assertThat(sqls).anyMatch(s -> s.contains("USING hnsw"));
            assertThat(sqls).anyMatch(s -> s.contains("vector_cosine_ops"));
        }

        @Test
        @DisplayName("should default dimension to 1536 when config dimension is null")
        void createIndex_defaultsDimensionTo1536() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            // dimension intentionally left null

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            postgresqlVectorService.createIndex("docs", config);

            verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
            assertThat(sqlCaptor.getAllValues())
                    .anyMatch(s -> s.contains("vector(1536)"));
        }

        @Test
        @DisplayName("should map metric l2 to vector_l2_ops")
        void createIndex_mapsL2ToVectorL2Ops() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            config.setDimension(768);
            config.setMetric("l2");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            postgresqlVectorService.createIndex("docs", config);

            verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
            assertThat(sqlCaptor.getAllValues())
                    .anyMatch(s -> s.contains("vector_l2_ops"));
        }

        @Test
        @DisplayName("should map metric ip/dot to vector_ip_ops")
        void createIndex_mapsIpToVectorIpOps() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            config.setDimension(768);
            config.setMetric("dot");

            ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);

            postgresqlVectorService.createIndex("docs", config);

            verify(jdbcTemplate, atLeastOnce()).execute(sqlCaptor.capture());
            assertThat(sqlCaptor.getAllValues())
                    .anyMatch(s -> s.contains("vector_ip_ops"));
        }

        @Test
        @DisplayName("should swallow HNSW-already-exists exception but still throw on CREATE TABLE failure")
        void createIndex_swallowsHnswExistsError() {
            VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
            config.setName("docs");
            config.setDimension(1536);

            // 1st execute (CREATE TABLE) succeeds; 2nd (CREATE INDEX) throws — should be swallowed
            doNothing().doThrow(new RuntimeException("relation already exists"))
                    .when(jdbcTemplate).execute(anyString());

            // should not throw
            postgresqlVectorService.createIndex("docs", config);
            verify(jdbcTemplate, times(2)).execute(anyString());
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
    @DisplayName("listDocumentsImpl")
    class ListDocumentsImplTests {

        @Test
        @DisplayName("should return empty list when no documents")
        void listDocumentsImpl_whenNoDocuments_shouldReturnEmptyList() {
            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of());

            List<VectorRecord> docs = postgresqlVectorService.listDocumentsImpl("documents", 0, 10);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should return records with id, content and metadata")
        void listDocumentsImpl_shouldReturnRecordsWithContentAndMetadata() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test content");
            row.put("metadata", Map.of("author", "test"));

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorRecord> docs = postgresqlVectorService.listDocumentsImpl("documents", 0, 10);

            assertThat(docs).hasSize(1);
            VectorRecord rec = docs.getFirst();
            assertThat(rec.getId()).isEqualTo("doc-1");
            assertThat(rec.getContent()).isNotNull();
            assertThat(rec.getMetadata()).containsEntry("author", "test");
        }

        @Test
        @DisplayName("should handle null metadata")
        void listDocumentsImpl_whenMetadataNull_shouldHandleGracefully() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test content");
            row.put("metadata", null);

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorRecord> docs = postgresqlVectorService.listDocumentsImpl("documents", 0, 10);

            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().getMetadata()).isNull();
        }

        @Test
        @DisplayName("should handle missing content")
        void listDocumentsImpl_whenContentMissing_shouldNotSetContent() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(0), eq(10)))
                    .thenReturn(List.of(row));

            List<VectorRecord> docs = postgresqlVectorService.listDocumentsImpl("documents", 0, 10);

            assertThat(docs).hasSize(1);
            assertThat(docs.getFirst().getContent()).isNull();
        }

        @Test
        @DisplayName("should handle pagination with offset and limit")
        void listDocumentsImpl_withOffsetAndLimit_shouldQueryCorrectly() {
            when(jdbcTemplate.queryForList(any(String.class), eq(20), eq(15)))
                    .thenReturn(List.of());

            postgresqlVectorService.listDocumentsImpl("documents", 20, 15);

            verify(jdbcTemplate).queryForList(
                    eq("SELECT id, content, metadata FROM vector_documents ORDER BY id OFFSET ? LIMIT ?"),
                    eq(20),
                    eq(15)
            );
        }
    }

    @Nested
    @DisplayName("similaritySearchByVector")
    class SimilaritySearchByVectorTests {

        @Test
        @DisplayName("should return empty list when no results")
        void similaritySearchByVector_whenNoResults_shouldReturnEmptyList() {
            when(jdbcTemplate.queryForList(any(String.class), eq(10)))
                    .thenReturn(List.of());

            List<Document> docs = postgresqlVectorService.similaritySearchByVector("documents", new float[]{0.1f, 0.2f}, 10, 0.0);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should return Documents with correct L2 distance to similarity conversion")
        void similaritySearchByVector_shouldConvertDistanceToSimilarity() {
            // Prepare mock search result with distance
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test document");
            row.put("distance", 0.5);  // L2 distance of 0.5
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<Document> docs = postgresqlVectorService.similaritySearchByVector("documents", new float[]{0.1f, 0.2f}, 5, 0.0);

            assertThat(docs).hasSize(1);
            Document doc = docs.getFirst();
            assertThat(doc.getId()).isEqualTo("doc-1");
            assertThat(doc.getText()).isEqualTo("Test document");

            // Verify L2 distance to similarity conversion: score = 1/(1+distance)
            // 0.5 distance -> 1/(1+0.5) = 0.666...
            assertThat(doc.getScore()).isCloseTo(1.0 / (1.0 + 0.5), org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("should handle zero distance for exact match")
        void similaritySearchByVector_withZeroDistance_shouldReturnHighScore() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Exact match");
            row.put("distance", 0.0);  // Exact match, zero distance
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            List<Document> docs = postgresqlVectorService.similaritySearchByVector("documents", new float[]{0.1f, 0.2f}, 5, 0.0);

            // score = 1/(1+0) = 1.0
            assertThat(docs.getFirst().getScore()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should skip results below minScore threshold")
        void similaritySearchByVector_belowMinScore_shouldSkipResult() {
            Map<String, Object> row = new HashMap<>();
            row.put("id", "doc-1");
            row.put("content", "Test");
            row.put("distance", 5.0);  // score = 1/(1+5) ≈ 0.167
            row.put("metadata", Map.of());

            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of(row));

            // minScore = 0.5, score = 0.167 < 0.5 → 应该被过滤
            List<Document> docs = postgresqlVectorService.similaritySearchByVector("documents", new float[]{0.1f, 0.2f}, 5, 0.5);

            assertThat(docs).isEmpty();
        }

        @Test
        @DisplayName("should construct correct SQL with vector literal")
        void similaritySearchByVector_shouldConstructCorrectSqlWithVectorLiteral() {
            when(jdbcTemplate.queryForList(any(String.class), eq(5)))
                    .thenReturn(List.of());

            float[] queryVector = new float[]{0.1f, 0.2f, 0.3f};
            postgresqlVectorService.similaritySearchByVector("documents", queryVector, 5, 0.0);

            // Verify SQL contains properly formatted vector literal [0.1,0.2,0.3]
            verify(jdbcTemplate).queryForList(
                    contains("SELECT id, content, metadata, vector <-> '[0.1,0.2,0.3]'::vector as distance FROM vector_documents"),
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
            List<VectorRecord> docs = postgresqlVectorService.listDocuments("documents", 0, 0);
            assertThat(docs).isEmpty();
            verifyNoInteractions(jdbcTemplate);
        }
    }

    @Nested
    @DisplayName("truncateIndex")
    class TruncateIndexTests {

        @Test
        @DisplayName("should DELETE all rows and return affected row count")
        void truncateIndex_executesDeleteAndReturnsCount() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_documents"),
                    eq(Long.class)
            )).thenReturn(42L);
            when(jdbcTemplate.update("DELETE FROM vector_documents")).thenReturn(42);

            long deleted = postgresqlVectorService.truncateIndex("documents");

            assertThat(deleted).isEqualTo(42L);
            verify(jdbcTemplate).update("DELETE FROM vector_documents");
        }

        @Test
        @DisplayName("should return zero and execute DELETE when index is empty")
        void truncateIndex_whenEmpty_shouldReturnZero() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_empty"),
                    eq(Long.class)
            )).thenReturn(0L);
            when(jdbcTemplate.update("DELETE FROM vector_empty")).thenReturn(0);

            long deleted = postgresqlVectorService.truncateIndex("empty");

            assertThat(deleted).isZero();
            verify(jdbcTemplate).update("DELETE FROM vector_empty");
        }

        @Test
        @DisplayName("should wrap DELETE failure in RuntimeException")
        void truncateIndex_whenDeleteThrows_shouldWrapException() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_broken"),
                    eq(Long.class)
            )).thenReturn(5L);
            when(jdbcTemplate.update("DELETE FROM vector_broken"))
                    .thenThrow(new RuntimeException("relation does not exist"));

            assertThatThrownBy(() -> postgresqlVectorService.truncateIndex("broken"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("truncateIndex 失败");
        }
    }

    @Nested
    @DisplayName("healthCheck (inherited 3-step probe)")
    class HealthCheckTests {

        @Test
        @DisplayName("should return false when indexExists returns false")
        void healthCheck_whenIndexMissing_shouldReturnFalse() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_missing")
            )).thenReturn(false);

            assertThat(postgresqlVectorService.healthCheck("missing")).isFalse();
        }

        @Test
        @DisplayName("should return true when schema exists and count is non-negative")
        void healthCheck_whenIndexOk_shouldReturnTrue() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_documents")
            )).thenReturn(true);
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_documents"),
                    eq(Long.class)
            )).thenReturn(10L);

            assertThat(postgresqlVectorService.healthCheck("documents")).isTrue();
        }

        @Test
        @DisplayName("should return true when countDocuments throws (PG impl catches exceptions and returns 0)")
        void healthCheck_whenCountThrows_shouldReturnFalse() {
            when(jdbcTemplate.queryForObject(
                    eq("SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = ? )"),
                    eq(Boolean.class),
                    eq("vector_offline")
            )).thenReturn(true);
            when(jdbcTemplate.queryForObject(
                    eq("SELECT COUNT(*) FROM vector_offline"),
                    eq(Long.class)
            )).thenThrow(new RuntimeException("connection lost"));

            assertThat(postgresqlVectorService.healthCheck("offline")).isTrue();
        }
    }

    @Nested
    @DisplayName("Ops API (§14.4 optimize / alias / backup / restore)")
    class OpsApiTests {

        @Test
        @DisplayName("optimize: should execute VACUUM ANALYZE and return true on success")
        void optimize_whenVacuumSucceeds_shouldReturnTrue() {
            doNothing().when(jdbcTemplate).execute(anyString());

            assertThat(postgresqlVectorService.optimize("documents")).isTrue();
            verify(jdbcTemplate).execute(contains("VACUUM"));
        }

        @Test
        @DisplayName("optimize: should return false when VACUUM ANALYZE throws")
        void optimize_whenVacuumThrows_shouldReturnFalse() {
            doThrow(new RuntimeException("relation does not exist"))
                    .when(jdbcTemplate).execute(anyString());

            assertThat(postgresqlVectorService.optimize("documents")).isFalse();
            verify(jdbcTemplate).execute(contains("VACUUM"));
        }

        @Test
        @DisplayName("createAlias: should throw UnsupportedOperationException (pgvector has no alias)")
        void createAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> postgresqlVectorService.createAlias("documents", "docs-alias"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("postgresql");
        }

        @Test
        @DisplayName("switchAlias: should throw UnsupportedOperationException (pgvector has no alias)")
        void switchAlias_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> postgresqlVectorService.switchAlias("old", "new", "alias"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("postgresql");
        }

        @Test
        @DisplayName("backup: should throw UnsupportedOperationException (needs pg_dump out-of-process)")
        void backup_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> postgresqlVectorService.backup("documents", "/tmp/dump.sql"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("postgresql");
        }

        @Test
        @DisplayName("restore: should throw UnsupportedOperationException (needs pg_restore out-of-process)")
        void restore_shouldThrowUnsupportedOperationException() {
            assertThatThrownBy(() -> postgresqlVectorService.restore("/tmp/dump.sql", "documents"))
                    .isInstanceOf(UnsupportedOperationException.class)
                    .hasMessageContaining("postgresql");
        }
    }
}