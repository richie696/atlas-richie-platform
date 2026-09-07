/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.MilvusVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.MilvusAdvancedSearchOperations;
import cn.richie696.component.vector.service.impl.MilvusAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.impl.MilvusVectorServiceImpl;
import cn.richie696.component.vector.topology.VectorCapability;
import cn.richie696.component.vector.topology.VectorCapabilityDescriptor;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorProviderFactory;
import cn.richie696.component.vector.topology.VectorScoreSemantics;
import cn.richie696.component.vector.topology.VectorScoreThresholdExecution;
import cn.richie696.component.vector.topology.VectorScoreThresholdKind;
import cn.richie696.component.vector.topology.VectorStoreCapabilities;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import io.milvus.client.MilvusServiceClient;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.milvus.MilvusVectorStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Named Multi-store factory for Milvus. */
public final class MilvusVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "host", "port", "username", "password", "database-name",
            "connect-timeout-ms", "keep-alive-time-ms", "keep-alive-timeout-ms",
            "keep-alive-without-calls", "idle-timeout-ms", "secure", "server-pem-path",
            "server-name", "ca-pem-path", "client-key-path", "client-pem-path");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall", "schema", "declared-scalar-fields")),
            new VectorCapabilityDescriptor(VectorCapability.QUERY_TUNING, "1.0",
                    Map.of("api", VectorAdvancedSearchOperations.class.getName(),
                            "supported", "HNSW:ef,IVF_*:nprobe")),
            VectorCapabilityDescriptor.supported(VectorCapability.SCORE_STAGES),
            VectorCapabilityDescriptor.supported(VectorCapability.INDEX_LIFECYCLE)));

    private final RerankService rerankService;

    public MilvusVectorProviderFactory(RerankService rerankService) {
        this.rerankService = rerankService;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.MILVUS;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        Set<String> unknown = new java.util.LinkedHashSet<>(definition.settings().keySet());
        unknown.removeAll(CONNECTION_KEYS);
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown Milvus connection settings: " + unknown);
        }
        MilvusConfig config = connectionConfig(definition.settings());
        if (config.getPort() < 1 || config.getPort() > 65535) {
            throw new IllegalArgumentException("Milvus port must be between 1 and 65535");
        }
        if (config.getConnectTimeoutMs() <= 0 || config.getKeepAliveTimeMs() <= 0
                || config.getKeepAliveTimeoutMs() <= 0 || config.getIdleTimeoutMs() <= 0) {
            throw new IllegalArgumentException("Milvus timeout settings must be greater than zero");
        }
        if ((config.getClientKeyPath() == null) != (config.getClientPemPath() == null)) {
            throw new IllegalArgumentException("Milvus client-key-path and client-pem-path must be configured together");
        }
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        requirePhysicalName(store.defaultIndex(), "default index");
        for (VectorIndexDefinition index : store.indexes().values()) {
            requirePhysicalName(index.name(), "index name");
            indexType(index.indexType());
            metricType(index.metric());
        }
    }

    @Override
    public VectorStoreCapabilities capabilities(
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        requireProvider(connection);
        return hybridEnabled(store) ? hybridCapabilities() : CAPABILITIES;
    }

    @Override
    public Set<String> physicalResourceIdentities(
            VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        Set<String> identities = new java.util.LinkedHashSet<>();
        if (store.indexes().isEmpty()) {
            identities.add("collection:" + store.defaultIndex());
        } else {
            store.indexes().values().forEach(index -> identities.add("collection:" + index.name()));
        }
        return Set.copyOf(identities);
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        MilvusConfig config = connectionConfig(definition.settings());
        return new MilvusConnectionHandle(definition.id(), openClient(config), config);
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof MilvusConnectionHandle milvusConnection)
                || connection.provider() != VectorProvider.MILVUS
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("Milvus factory received an incompatible connection handle");
        }
        validateStore(
                new VectorConnectionDefinition(connection.id(), provider(), Map.of()),
                definition);

        MilvusConfig storeConfig = copyConnectionConfig(milvusConnection.config());
        storeConfig.setCollectionName(definition.defaultIndex());
        storeConfig.setEmbeddingDimension(embeddingModel.dimensions() > 0
                ? embeddingModel.dimensions() : storeConfig.getEmbeddingDimension());
        defaultIndex(definition).ifPresent(index -> {
            storeConfig.setEmbeddingDimension(index.dimension());
            storeConfig.setIndexType(indexType(index.indexType()));
            storeConfig.setMetricType(metricType(index.metric()));
        });

        VectorStore vectorStore = MilvusVectorStore.builder(milvusConnection.client(), embeddingModel.model())
                .databaseName(storeConfig.getDatabaseName())
                .collectionName(storeConfig.getCollectionName())
                .indexType(storeConfig.getIndexType())
                .metricType(storeConfig.getMetricType())
                .batchingStrategy(new TokenCountBatchingStrategy())
                .initializeSchema(false)
                .build();
        MilvusVectorServiceImpl service = new MilvusVectorServiceImpl(
                rerankService, vectorStore, embeddingModel.model(), storeConfig, milvusConnection.client(),
                hybridEnabled(definition) ? milvusConnection.hybridClient() : null);
        service.setVectorProperties(storeProperties(definition));
        VectorFilterCompiler filterCompiler = new MilvusVectorFilterCompiler();
        service.setVectorFilterCompiler(filterCompiler);
        String boundIndexType = defaultIndex(definition)
                .map(VectorIndexDefinition::indexType)
                .orElse(storeConfig.getIndexType().name());
        VectorAdvancedSearchOperations advancedSearch = new MilvusAdvancedSearchOperations(
                service,
                definition.id(),
                definition.defaultIndex(),
                boundIndexType,
                (VectorQueryDefaults) null,
                definition.queryDefaults(),
                scoreSemantics(metricType(defaultIndex(definition)
                        .map(VectorIndexDefinition::metric)
                        .orElse("cosine"))));

        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(capabilities(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition))
                .capability(VectorFilterCompiler.class, filterCompiler)
                .capability(VectorScoreSemantics.class, VectorScoreSemantics.finalScore(
                        "milvus.adapter", 0.0D, 1.0D, true, "metric-aware-conversion-clamped",
                        VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                        VectorScoreThresholdExecution.ADAPTER_NORMALIZED))
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service)
                .capability(VectorAdvancedSearchOperations.class, advancedSearch);
        if (hybridEnabled(definition)) {
            handle.capability(VectorAclAwareHybridSearchOperations.class,
                    new MilvusAclAwareHybridSearchOperations(
                            milvusConnection.hybridClient(), embeddingModel.model(), filterCompiler));
        }
        return handle.build();
    }

    static MilvusServiceClient openClient(MilvusConfig config) {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(config.getHost())
                .withPort(config.getPort())
                .withConnectTimeout(config.getConnectTimeoutMs(), TimeUnit.MILLISECONDS)
                .withKeepAliveTime(config.getKeepAliveTimeMs(), TimeUnit.MILLISECONDS)
                .withKeepAliveTimeout(config.getKeepAliveTimeoutMs(), TimeUnit.MILLISECONDS)
                .withIdleTimeout(config.getIdleTimeoutMs(), TimeUnit.MILLISECONDS);
        if (config.getUsername() != null && config.getPassword() != null) {
            builder.withAuthorization(config.getUsername(), config.getPassword());
        }
        if (config.isSecure()) {
            if (config.getServerPemPath() != null) builder.withServerPemPath(config.getServerPemPath());
            if (config.getServerName() != null) builder.withServerName(config.getServerName());
            if (config.getCaPemPath() != null) builder.withCaPemPath(config.getCaPemPath());
            if (config.getClientKeyPath() != null && config.getClientPemPath() != null) {
                builder.withClientKeyPath(config.getClientKeyPath()).withClientPemPath(config.getClientPemPath());
            }
        }
        return new MilvusServiceClient(builder.build());
    }

    static MilvusClientV2 openHybridClient(MilvusConfig config) {
        String scheme = config.isSecure() ? "https" : "http";
        ConnectConfig.ConnectConfigBuilder builder = ConnectConfig.builder()
                .uri(scheme + "://" + config.getHost() + ":" + config.getPort())
                .dbName(config.getDatabaseName())
                .connectTimeoutMs(config.getConnectTimeoutMs())
                .keepAliveTimeMs(config.getKeepAliveTimeMs())
                .keepAliveTimeoutMs(config.getKeepAliveTimeoutMs())
                .keepAliveWithoutCalls(config.isKeepAliveWithoutCalls())
                .idleTimeoutMs(config.getIdleTimeoutMs())
                .secure(config.isSecure())
                .serverPemPath(config.getServerPemPath())
                .serverName(config.getServerName())
                .caPemPath(config.getCaPemPath())
                .clientKeyPath(config.getClientKeyPath())
                .clientPemPath(config.getClientPemPath());
        if (config.getUsername() != null && config.getPassword() != null) {
            builder.username(config.getUsername()).password(config.getPassword());
        }
        return new MilvusClientV2(builder.build());
    }

    private static MilvusConfig connectionConfig(Map<String, Object> settings) {
        MilvusConfig config = new MilvusConfig();
        config.setHost(text(settings, "host", config.getHost()));
        config.setPort(integer(settings, "port", config.getPort()));
        config.setUsername(nullableText(settings, "username"));
        config.setPassword(nullableText(settings, "password"));
        config.setDatabaseName(text(settings, "database-name", config.getDatabaseName()));
        config.setConnectTimeoutMs(longValue(settings, "connect-timeout-ms", config.getConnectTimeoutMs()));
        config.setKeepAliveTimeMs(longValue(settings, "keep-alive-time-ms", config.getKeepAliveTimeMs()));
        config.setKeepAliveTimeoutMs(longValue(settings, "keep-alive-timeout-ms", config.getKeepAliveTimeoutMs()));
        config.setKeepAliveWithoutCalls(booleanValue(
                settings, "keep-alive-without-calls", config.isKeepAliveWithoutCalls()));
        config.setIdleTimeoutMs(longValue(settings, "idle-timeout-ms", config.getIdleTimeoutMs()));
        config.setSecure(booleanValue(settings, "secure", config.isSecure()));
        config.setServerPemPath(nullableText(settings, "server-pem-path"));
        config.setServerName(nullableText(settings, "server-name"));
        config.setCaPemPath(nullableText(settings, "ca-pem-path"));
        config.setClientKeyPath(nullableText(settings, "client-key-path"));
        config.setClientPemPath(nullableText(settings, "client-pem-path"));
        return config;
    }

    private static MilvusConfig copyConnectionConfig(MilvusConfig source) {
        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("host", source.getHost());
        settings.put("port", source.getPort());
        settings.put("database-name", source.getDatabaseName());
        settings.put("connect-timeout-ms", source.getConnectTimeoutMs());
        settings.put("keep-alive-time-ms", source.getKeepAliveTimeMs());
        settings.put("keep-alive-timeout-ms", source.getKeepAliveTimeoutMs());
        settings.put("keep-alive-without-calls", source.isKeepAliveWithoutCalls());
        settings.put("idle-timeout-ms", source.getIdleTimeoutMs());
        settings.put("secure", source.isSecure());
        putIfNotNull(settings, "username", source.getUsername());
        putIfNotNull(settings, "password", source.getPassword());
        putIfNotNull(settings, "server-pem-path", source.getServerPemPath());
        putIfNotNull(settings, "server-name", source.getServerName());
        putIfNotNull(settings, "ca-pem-path", source.getCaPemPath());
        putIfNotNull(settings, "client-key-path", source.getClientKeyPath());
        putIfNotNull(settings, "client-pem-path", source.getClientPemPath());
        return connectionConfig(settings);
    }

    private static VectorProperties storeProperties(VectorStoreDefinition store) {
        VectorProperties properties = new VectorProperties();
        properties.setDefaultIndex(store.defaultIndex());
        Map<String, VectorProperties.IndexConfig> indexes = new LinkedHashMap<>();
        store.indexes().forEach((id, index) -> indexes.put(id, new VectorProperties.IndexConfig()
                .setName(index.name())
                .setDimension(index.dimension())
                .setMetric(index.metric())
                .setIndexType(index.indexType())
                .setReplicas(index.replicas())
                .setShards(index.shards())
                .setAdditionalFields(index.additionalFields())
                .setIndexParams(index.indexParams())));
        properties.setIndexes(indexes);
        return properties;
    }

    private static java.util.Optional<VectorIndexDefinition> defaultIndex(VectorStoreDefinition store) {
        VectorIndexDefinition byId = store.indexes().get(store.defaultIndex());
        if (byId != null) return java.util.Optional.of(byId);
        return store.indexes().values().stream().filter(index -> index.name().equals(store.defaultIndex())).findFirst();
    }

    private static boolean hybridEnabled(VectorStoreDefinition store) {
        return defaultIndex(store)
                .map(VectorIndexDefinition::additionalFields)
                .map(fields -> fields.get("hybrid-enabled"))
                .map(value -> value instanceof Boolean bool ? bool : Boolean.parseBoolean(value.toString()))
                .orElse(false);
    }

    private static VectorStoreCapabilities hybridCapabilities() {
        List<VectorCapabilityDescriptor> descriptors = new java.util.ArrayList<>(CAPABILITIES.descriptors());
        descriptors.add(new VectorCapabilityDescriptor(VectorCapability.ACL_SAFE_HYBRID, "1.0",
                Map.of("transport", "milvus-v2-hybrid-search", "branches", "dense,bm25",
                        "filter-stage", "provider-recall", "schema", "hybrid-enabled")));
        return new VectorStoreCapabilities(descriptors);
    }

    private static VectorScoreSemantics scoreSemantics(MetricType metric) {
        return switch (metric) {
            case COSINE -> VectorScoreSemantics.finalScore(
                    "milvus.adapter.cosine", 0.0D, 1.0D, true, "clamp((1+cos)/2)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case IP -> VectorScoreSemantics.finalScore(
                    "milvus.adapter.inner-product", null, null, false, "raw-inner-product",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case L2 -> VectorScoreSemantics.finalScore(
                    "milvus.adapter.euclidean", 0.0D, 1.0D, true, "1.0/(1.0+distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "milvus.adapter", 0.0D, 1.0D, true, "metric-aware-conversion-clamped",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static IndexType indexType(String value) {
        try {
            return IndexType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported Milvus index type: " + value);
        }
    }

    private static MetricType metricType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "cosine" -> MetricType.COSINE;
            case "euclidean", "l2" -> MetricType.L2;
            case "dot", "ip" -> MetricType.IP;
            default -> throw new IllegalArgumentException("unsupported Milvus metric: " + value);
        };
    }

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.MILVUS) {
            throw new IllegalArgumentException("Milvus factory cannot handle provider " + definition.provider());
        }
    }

    private static void requirePhysicalName(String value, String field) {
        if (value == null || !value.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid Milvus " + field);
        }
    }

    private static String text(Map<String, Object> settings, String key, String fallback) {
        Object value = settings.get(key);
        if (value == null) return fallback;
        String text = String.valueOf(value);
        if (text.isBlank()) throw new IllegalArgumentException("Milvus setting must not be blank: " + key);
        return text;
    }

    private static String nullableText(Map<String, Object> settings, String key) {
        Object value = settings.get(key);
        if (value == null) return null;
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private static int integer(Map<String, Object> settings, String key, int fallback) {
        Object value = settings.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Milvus setting must be an integer: " + key);
        }
    }

    private static long longValue(Map<String, Object> settings, String key, long fallback) {
        Object value = settings.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Milvus setting must be a long: " + key);
        }
    }

    private static boolean booleanValue(Map<String, Object> settings, String key, boolean fallback) {
        Object value = settings.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("Milvus setting must be a boolean: " + key);
    }

    private static void putIfNotNull(Map<String, Object> settings, String key, Object value) {
        if (value != null) settings.put(key, value);
    }

    private static final class MilvusConnectionHandle implements VectorConnectionHandle {
        private final VectorConnectionId id;
        private final MilvusServiceClient client;
        private final MilvusConfig config;
        private MilvusClientV2 hybridClient;

        private MilvusConnectionHandle(VectorConnectionId id, MilvusServiceClient client, MilvusConfig config) {
            this.id = id;
            this.client = client;
            this.config = config;
        }

        @Override
        public VectorConnectionId id() {
            return id;
        }

        private MilvusServiceClient client() {
            return client;
        }

        private MilvusConfig config() {
            return config;
        }

        private synchronized MilvusClientV2 hybridClient() {
            if (hybridClient == null) hybridClient = openHybridClient(config);
            return hybridClient;
        }

        @Override
        public VectorProvider provider() {
            return VectorProvider.MILVUS;
        }

        @Override
        public void close() {
            client.close();
            if (hybridClient != null) hybridClient.close();
        }

        @Override
        public String toString() {
            return "MilvusConnectionHandle[id=" + id + ", provider=MILVUS, config=<redacted>]";
        }
    }
}
