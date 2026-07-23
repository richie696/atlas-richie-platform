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
package com.richie.component.vector.config;

import com.richie.component.vector.config.VectorProperties.IndexConfig;
import com.richie.component.vector.model.HybridSearchOptions;
import com.richie.component.vector.model.IndexInfo;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.SearchOptions;
import com.richie.component.vector.model.VectorContent;
import com.richie.component.vector.model.VectorRecord;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class VectorMultiProviderGuardTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void guard_withSingleProvider_shouldNotThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new StubVectorService("RedisVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = newGuard();

        assertDoesNotThrow(guard::guard);
    }

    @Test
    void guard_withMultipleProviders_shouldThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new StubVectorService("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new StubVectorService("MilvusVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = newGuard();

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("检测到多个 VectorService 实现被同时引入"));
        assertTrue(exception.getMessage().contains("redisVectorService"));
        assertTrue(exception.getMessage().contains("milvusVectorService"));
    }

    @Test
    void guard_withThreeProviders_shouldListAll() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new StubVectorService("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new StubVectorService("MilvusVectorServiceImpl"));
        beans.put("neo4jVectorService", new StubVectorService("Neo4jVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = newGuard();

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("redisVectorService"));
        assertTrue(exception.getMessage().contains("milvusVectorService"));
        assertTrue(exception.getMessage().contains("neo4jVectorService"));
    }

    private VectorMultiProviderGuard newGuard() {
        VectorMultiProviderGuard guard = new VectorMultiProviderGuard(applicationContext);
        return guard;
    }

    /**
     * VectorService v2 接口的桩实现 — 仅用于验证多 provider 防护逻辑，
     * 所有方法返回 null / 空集合，不抛异常。
     */
    private static class StubVectorService implements VectorService {
        private final String name;

        StubVectorService(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        // ===== Add =====
        @Override public String addText(String indexName, String text, Map<String, Object> metadata) { return null; }
        @Override public String add(VectorRecord record) { return null; }
        @Override public String addImage(String indexName, byte[] image, String mimeType, Map<String, Object> metadata) { return null; }
        @Override public String addImage(String indexName, java.nio.file.Path imagePath, String mimeType, Map<String, Object> metadata) { return null; }
        @Override public String addImageUrl(String indexName, String url, String mimeType, Map<String, Object> metadata) { return null; }

        // ===== Update =====
        @Override public void updateText(String indexName, String id, String text, Map<String, Object> metadata) {}
        @Override public void update(VectorRecord record) {}
        @Override public void updateImage(String indexName, String id, byte[] image, String mimeType, Map<String, Object> metadata) {}
        @Override public void updateImage(String indexName, String id, java.nio.file.Path imagePath, String mimeType, Map<String, Object> metadata) {}

        // ===== Delete =====
        @Override public void delete(String indexName, String id) {}
        @Override public long deleteIf(String indexName, Predicate<VectorRecord> filter) { return 0L; }

        // ===== Get =====
        @Override public Optional<VectorRecord> get(String indexName, String id) { return Optional.empty(); }
        @Override public List<VectorRecord> getAll(String indexName, Collection<String> ids) { return List.of(); }

        // ===== Search =====
        @Override public List<VectorSearchResult> searchByText(String indexName, String text, int limit) { return List.of(); }
        @Override public List<VectorSearchResult> searchByText(String indexName, String text, int limit, double minScore) { return List.of(); }
        @Override public List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options) { return List.of(); }
        @Override public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit) { return List.of(); }
        @Override public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore) { return List.of(); }
        @Override public List<VectorSearchResult> searchByImage(String indexName, java.nio.file.Path imagePath, String mimeType, int limit) { return List.of(); }
        @Override public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery, int limit, HybridSearchOptions options) { return List.of(); }
        @Override public List<VectorSearchResult> searchByMultiVector(String indexName, List<float[]> vectors, int limit) { return List.of(); }

        // ===== Index Management =====
        @Override public void createIndex(String indexName, IndexConfig config) {}
        @Override public void deleteIndex(String indexName) {}
        @Override public boolean indexExists(String indexName) { return false; }
        @Override public IndexConfig getIndexConfig(String indexName) { return null; }
        @Override public long countDocuments(String indexName) { return 0L; }
        @Override public List<VectorRecord> listDocuments(String indexName, int offset, int limit) { return List.of(); }
        @Override public List<IndexInfo> listIndexes() { return List.of(); }
        @Override public long truncateIndex(String indexName) { return 0L; }
        @Override public boolean updateIndexConfig(String indexName, IndexConfig config) { return false; }
        @Override public boolean cloneIndex(String sourceIndexName, String targetIndexName) { return false; }
        @Override public boolean awaitIndexReady(String indexName, java.time.Duration timeout) { return false; }
        @Override public IndexInfo describeIndex(String indexName) { return null; }

        // ===== Stats / Health =====
        @Override public IndexInfo getIndexStats(String indexName) { return null; }
        @Override public boolean healthCheck(String indexName) { return false; }

        // ===== Maintenance / Alias / Backup =====
        @Override public boolean optimize(String indexName) { return false; }
        @Override public boolean createAlias(String indexName, String alias) { return false; }
        @Override public boolean switchAlias(String oldIndexName, String newIndexName, String alias) { return false; }
        @Override public boolean backup(String indexName, String targetPath) { return false; }
        @Override public boolean restore(String sourcePath, String indexName) { return false; }

        // ===== Batch Async Reactive =====
        @Override public Flux<com.richie.component.vector.model.BatchEvent> addBatch(String indexName, Flux<VectorRecord> records) { return Flux.empty(); }
        @Override public Flux<com.richie.component.vector.model.BatchEvent> addBatch(String indexName, List<VectorRecord> records) { return Flux.empty(); }
        @Override public Flux<com.richie.component.vector.model.BatchEvent> updateBatch(String indexName, Flux<VectorRecord> records) { return Flux.empty(); }
        @Override public Flux<com.richie.component.vector.model.BatchEvent> deleteBatch(String indexName, Flux<String> ids) { return Flux.empty(); }
    }
}