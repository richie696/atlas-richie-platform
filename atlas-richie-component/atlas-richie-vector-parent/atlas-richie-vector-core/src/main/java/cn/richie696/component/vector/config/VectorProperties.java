/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.query.VectorConsistencyPreference;
import cn.richie696.component.vector.topology.EmbeddingNormalization;
import cn.richie696.component.vector.topology.VectorCapability;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 向量存储组件配置；模型厂商和 API 凭据由 AI 组件管理。
 */
@Data
@ConfigurationProperties(prefix = "platform.component.vector")
public class VectorProperties {

    private static final Pattern TOPOLOGY_ID = Pattern.compile("[a-z0-9]+(?:[-_][a-z0-9]+)*");
    private static final Pattern CAPABILITY_ID = Pattern.compile("[A-Z][A-Z0-9_]*");

    /**
     * 当前启用的单一向量 provider。
     */
    private VectorProvider provider = VectorProvider.MILVUS;

    /**
     * 默认索引/collection 名称。
     */
    private String defaultIndex = "documents";

    /**
     * 索引级声明配置。
     */
    private Map<String, IndexConfig> indexes;

    /**
     * Named physical connections used by the multi-store topology.
     *
     * <p>The value's {@link ConnectionConfig#settings} remain provider-owned:
     * core preserves them as data, while the selected provider factory is
     * responsible for strict key/type validation. This avoids coupling core to
     * every provider's credentials and transport options.</p>
     */
    private Map<String, ConnectionConfig> connections = new LinkedHashMap<>();

    /**
     * Named logical vector stores. Business code routes by this stable id, not
     * by provider type or by a Spring bean name.
     */
    private Map<String, StoreConfig> stores = new LinkedHashMap<>();

    /**
     * 仅在使用者验证该 provider 支持 Spring AI filter DSL 后开启。
     */
    private boolean springAiFilterDslEnabled;

    /**
     * 批量入库的背压、并发及刷盘参数。
     */
    @NestedConfigurationProperty
    private Bulk bulk = new Bulk();

    /**
     * Returns whether the new named topology has been configured.
     */
    public boolean hasNamedTopology() {
        return (connections != null && !connections.isEmpty()) || (stores != null && !stores.isEmpty());
    }

    /**
     * Validates provider-independent topology rules after configuration binding.
     * Provider factories perform the remaining strict validation of their
     * opaque {@link ConnectionConfig#settings}.
     */
    public void validateNamedTopology() {
        if (!hasNamedTopology()) {
            return;
        }
        if (connections == null || connections.isEmpty()) {
            throw new IllegalArgumentException("vector connections must not be empty when named stores are configured");
        }
        if (stores == null || stores.isEmpty()) {
            throw new IllegalArgumentException("vector stores must not be empty when named connections are configured");
        }
        connections.forEach((connectionId, connection) -> {
            validateTopologyId("connectionId", connectionId);
            if (connection == null || connection.getProvider() == null) {
                throw new IllegalArgumentException("vector connection provider is required: " + connectionId);
            }
            if (connection.getSettings() == null) {
                throw new IllegalArgumentException("vector connection settings must not be null: " + connectionId);
            }
            connection.getSettings().keySet().forEach(key -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("vector connection setting key must not be blank: " + connectionId);
                }
            });
        });
        stores.forEach((storeId, store) -> {
            validateTopologyId("vectorStoreId", storeId);
            if (store == null) {
                throw new IllegalArgumentException("vector store definition must not be null: " + storeId);
            }
            String connectionRef = store.getConnectionRef();
            if (connectionRef == null || connectionRef.isBlank()) {
                throw new IllegalArgumentException("vector store connection-ref is required: " + storeId);
            }
            validateTopologyId("connectionRef", connectionRef);
            if (!connections.containsKey(connectionRef)) {
                throw new IllegalArgumentException("vector store references unknown connection: " + storeId + " -> " + connectionRef);
            }
            if (store.getEmbeddingModelRef() == null || store.getEmbeddingModelRef().isBlank()) {
                throw new IllegalArgumentException("vector store embedding-model-ref is required: " + storeId);
            }
            if (store.getEmbeddingNormalization() == null) {
                throw new IllegalArgumentException("vector store embedding-normalization is required: " + storeId);
            }
            if (store.getEmbeddingModalities() == null || store.getEmbeddingModalities().isEmpty()
                    || store.getEmbeddingModalities().stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("vector store embedding-modalities must not be empty: " + storeId);
            }
            if (store.getDefaultIndex() == null || store.getDefaultIndex().isBlank()) {
                throw new IllegalArgumentException("vector store default-index is required: " + storeId);
            }
            if (store.getRequiredCapabilities() == null) {
                throw new IllegalArgumentException("vector store required-capabilities must not be null: " + storeId);
            }
            store.getRequiredCapabilities().forEach(capability -> {
                if (capability == null || !CAPABILITY_ID.matcher(capability).matches()) {
                    throw new IllegalArgumentException("invalid vector capability for store " + storeId + ": " + capability);
                }
                VectorCapability.fromId(capability);
            });
            if (store.getIndexes() == null) {
                throw new IllegalArgumentException("vector store indexes must not be null: " + storeId);
            }
            store.getIndexes().keySet().forEach(indexId -> validateTopologyId("indexId", indexId));
        });
    }

    private static void validateTopologyId(String field, String value) {
        if (value == null || !TOPOLOGY_ID.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must match " + TOPOLOGY_ID.pattern() + ": " + value);
        }
    }

    @Data
    @Accessors(chain = true)
    public static class ConnectionConfig {
        private VectorProvider provider;
        private Map<String, Object> settings = new LinkedHashMap<>();
    }

    @Data
    @Accessors(chain = true)
    public static class StoreConfig {
        private String connectionRef;
        private String embeddingModelRef = "aiEmbeddingModel";
        private EmbeddingNormalization embeddingNormalization = EmbeddingNormalization.UNSPECIFIED;
        private Set<Modality> embeddingModalities = new LinkedHashSet<>(Set.of(Modality.TEXT));
        private String defaultIndex = "documents";
        private boolean required = true;
        private Set<String> requiredCapabilities = new LinkedHashSet<>();
        @NestedConfigurationProperty
        private QueryDefaultsConfig queryDefaults = new QueryDefaultsConfig();
        private Map<String, IndexConfig> indexes = new LinkedHashMap<>();
    }

    /** Optional Store-level overrides; null/empty values inherit provider and Core defaults. */
    @Data
    @Accessors(chain = true)
    public static class QueryDefaultsConfig {
        private Integer topK;
        private Double minScore;
        private Integer candidateLimit;
        private Duration timeout;
        private VectorConsistencyPreference consistency;
        private Set<String> returnFields = new LinkedHashSet<>();
    }

    @Data
    @Accessors(chain = true)
    public static class IndexConfig {
        private String name;
        private Integer dimension = 1536;
        private String metric = "cosine";
        private String indexType = "hnsw";
        private Integer replicas = 1;
        private Integer shards = 1;
        private Map<String, Object> additionalFields;
        private Map<String, Object> indexParams;
    }

    @Data
    @Accessors(chain = true)
    public static class Bulk {
        /**
         * 同时进行的 embedding 调用数。
         */
        private int embeddingConcurrency = 8;
        /**
         * 单次向量库写入的记录数。
         */
        private int writeBatchSize = 100;
        /**
         * 同时进行的向量库写入数。
         */
        private int writeConcurrency = 4;
        /**
         * 不足一个写入批次时的最长等待时间。
         */
        private long writeFlushIntervalMs = 1_000;
    }
}
