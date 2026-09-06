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

import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorMultiProviderGuardTest {

    @Test
    void guard_withSingleProvider_shouldNotThrow() {
        GenericApplicationContext context = contextWith("redisVectorService");
        VectorMultiProviderGuard guard = newGuard(context, new VectorProperties());

        assertDoesNotThrow(guard::guard);
        context.close();
    }

    @Test
    void guard_withMultipleProviders_shouldThrow() {
        GenericApplicationContext context = contextWith("redisVectorService", "milvusVectorService");
        VectorMultiProviderGuard guard = newGuard(context, new VectorProperties());

        IllegalStateException exception = assertThrows(IllegalStateException.class, guard::guard);
        assertTrue(exception.getMessage().contains("检测到多个 VectorService 实现被同时引入"));
        assertTrue(exception.getMessage().contains("redisVectorService"));
        assertTrue(exception.getMessage().contains("milvusVectorService"));
        context.close();
    }

    @Test
    void guard_withNamedTopologyAndNoGlobalService_shouldNotThrow() {
        GenericApplicationContext context = contextWith();
        VectorProperties properties = namedProperties();

        assertDoesNotThrow(() -> newGuard(context, properties).guard());
        context.close();
    }

    @Test
    void guard_withSingleNamedCompatibilityService_shouldNotThrow() {
        GenericApplicationContext context = contextWith(VectorMultiProviderGuard.SINGLE_NAMED_COMPATIBILITY_BEAN);
        VectorProperties properties = namedProperties();

        assertDoesNotThrow(() -> newGuard(context, properties).guard());
        context.close();
    }

    @Test
    void guard_withNamedTopologyAndGlobalService_shouldThrow() {
        GenericApplicationContext context = contextWith("legacyVectorService");
        VectorProperties properties = namedProperties();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> newGuard(context, properties).guard());
        assertTrue(exception.getMessage().contains("Named vector topology"));
        assertTrue(exception.getMessage().contains("legacyVectorService"));
        context.close();
    }

    private static VectorMultiProviderGuard newGuard(
            GenericApplicationContext context,
            VectorProperties properties) {
        return new VectorMultiProviderGuard(context, properties);
    }

    private static GenericApplicationContext contextWith(String... beanNames) {
        GenericApplicationContext context = new GenericApplicationContext();
        for (String beanName : beanNames) {
            context.registerBean(beanName, VectorService.class, () -> new StubVectorService(beanName));
        }
        context.refresh();
        return context;
    }

    private static VectorProperties namedProperties() {
        VectorProperties properties = new VectorProperties();
        properties.setConnections(java.util.Map.of(
                "primary", new VectorProperties.ConnectionConfig()
                        .setProvider(cn.richie696.component.vector.enums.VectorProvider.MILVUS)));
        properties.setStores(java.util.Map.of(
                "primary", new VectorProperties.StoreConfig().setConnectionRef("primary")));
        return properties;
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

        @Override
        public String upsert(VectorRecord record) {
            return null;
        }

        @Override
        public void deleteById(String indexName, String vectorId) {
        }

        @Override
        public void deleteByIds(String indexName, Collection<String> vectorIds) {
        }

        @Override
        public List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore) {
            return List.of();
        }

        @Override
        public List<VectorSearchResult> searchByImage(String indexName, java.nio.file.Path imagePath, String mimeType, int limit) {
            return List.of();
        }

        @Override
        public Flux<cn.richie696.component.vector.bulk.BulkOperationEvent> upsertAll(String indexName, Flux<VectorRecord> records) {
            return Flux.empty();
        }

        @Override
        public Flux<cn.richie696.component.vector.bulk.BulkOperationEvent> deleteAll(String indexName, Flux<String> ids) {
            return Flux.empty();
        }
    }
}
