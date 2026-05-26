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

@ExtendWith(MockitoExtension.class)
class VectorMultiProviderGuardTest {

    @Mock
    private ApplicationContext applicationContext;

    @Test
    void guard_withSingleProvider_shouldNotThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = new VectorMultiProviderGuard();
        guard.guard();

        assertDoesNotThrow(() -> guard.guard());
    }

    @Test
    void guard_withMultipleProviders_shouldThrow() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new TestVectorServiceImpl("MilvusVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = new VectorMultiProviderGuard();

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("检测到多个 VectorService 实现被同时引入"));
        assertTrue(exception.getMessage().contains("RedisVectorServiceImpl"));
        assertTrue(exception.getMessage().contains("MilvusVectorServiceImpl"));
    }

    @Test
    void guard_withThreeProviders_shouldListAll() {
        Map<String, VectorService> beans = new HashMap<>();
        beans.put("redisVectorService", new TestVectorServiceImpl("RedisVectorServiceImpl"));
        beans.put("milvusVectorService", new TestVectorServiceImpl("MilvusVectorServiceImpl"));
        beans.put("neo4jVectorService", new TestVectorServiceImpl("Neo4jVectorServiceImpl"));

        when(applicationContext.getBeansOfType(VectorService.class)).thenReturn(beans);

        VectorMultiProviderGuard guard = new VectorMultiProviderGuard();

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("RedisVectorServiceImpl"));
        assertTrue(exception.getMessage().contains("MilvusVectorServiceImpl"));
        assertTrue(exception.getMessage().contains("Neo4jVectorServiceImpl"));
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