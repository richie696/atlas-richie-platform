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
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.config.VectorProperties.IndexConfig;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.IndexInfo;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorService;
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

        @Override public String upsert(VectorRecord record) { return null; }
        @Override public void deleteById(String indexName, String vectorId) { }
        @Override public void deleteByIds(String indexName, Collection<String> vectorIds) { }
        @Override public List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options) { return List.of(); }
        @Override public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore) { return List.of(); }
        @Override public List<VectorSearchResult> searchByImage(String indexName, java.nio.file.Path imagePath, String mimeType, int limit) { return List.of(); }
        @Override public Flux<cn.richie696.component.vector.bulk.BulkOperationEvent> upsertAll(String indexName, Flux<VectorRecord> records) { return Flux.empty(); }
        @Override public Flux<cn.richie696.component.vector.bulk.BulkOperationEvent> deleteAll(String indexName, Flux<String> ids) { return Flux.empty(); }
    }
}
