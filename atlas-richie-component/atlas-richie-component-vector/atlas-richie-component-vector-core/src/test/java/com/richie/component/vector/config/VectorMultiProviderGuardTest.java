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
package com.richie.component.vector.config;

import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorQuery;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.util.ReflectionTestUtils.setField;

@ExtendWith(MockitoExtension.class)
class VectorMultiProviderGuardTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void guard_withSingleProvider_shouldNotThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = newGuard();

        assertDoesNotThrow(guard::guard);
    }

    @Test
    void guard_withMultipleProviders_shouldThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new TestVectorServiceImpl("MilvusVectorServiceImpl"));

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
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new TestVectorServiceImpl("MilvusVectorServiceImpl"));
        beans.put("neo4jVectorService", new TestVectorServiceImpl("Neo4jVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = newGuard();

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("redisVectorService"));
        assertTrue(exception.getMessage().contains("milvusVectorService"));
        assertTrue(exception.getMessage().contains("neo4jVectorService"));
    }

    private VectorMultiProviderGuard newGuard() {
        VectorMultiProviderGuard guard = new VectorMultiProviderGuard();
        setField(guard, "applicationContext", applicationContext);
        return guard;
    }

    private static class TestVectorServiceImpl implements VectorService {
        private final String name;

        TestVectorServiceImpl(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public String addDocument(VectorDocument document) {
            return null;
        }

        @Override
        public List<String> addDocuments(List<VectorDocument> documents) {
            return List.of();
        }

        @Override
        public void updateDocument(String id, VectorDocument document) {
        }

        @Override
        public void deleteDocument(String id) {
        }

        @Override
        public void deleteDocuments(List<String> ids) {
        }

        @Override
        public VectorDocument getDocument(String id) {
            return null;
        }

        @Override
        public List<VectorDocument> getDocuments(List<String> ids) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> search(VectorQuery query) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByText(String text, int limit) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit, double minScore) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByText(String text, int limit, double minScore) {
            return List.of();
        }

        @Override
        public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        }

        @Override
        public void deleteIndex(String indexName) {
        }

        @Override
        public boolean indexExists(String indexName) {
            return false;
        }

        @Override
        public VectorProperties.IndexConfig getIndexConfig(String indexName) {
            return null;
        }

        @Override
        public long countDocuments(String indexName) {
            return 0;
        }

        @Override
        public List<VectorDocument> listDocuments(String indexName, int offset, int limit) {
            return List.of();
        }
    }
}