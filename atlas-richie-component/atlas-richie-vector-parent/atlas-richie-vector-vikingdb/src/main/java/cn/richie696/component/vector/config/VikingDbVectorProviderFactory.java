/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.ai.vectorstore.vikingdb.VikingDbVectorStore;
import cn.richie696.ai.vectorstore.vikingdb.VikingDbStoreSpec;
import cn.richie696.ai.vectorstore.vikingdb.VikingDbVectorStoreFactory;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbCollectionOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbDocumentOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbIndexOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbPermissionOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbRerankOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbSearchOperations;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbIndexVectorOptions;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.impl.VikingDbVectorServiceImpl;
import cn.richie696.component.vector.service.impl.VikingDbAdvancedSearchOperations;
import cn.richie696.component.vector.service.VectorAdvancedSearchOperations;
import cn.richie696.component.vector.query.VectorQueryDefaults;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
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
import com.volcengine.ApiClient;
import com.volcengine.sign.Credentials;
import com.volcengine.vikingdb.VikingdbApi;
import com.volcengine.vikingdb.model.FieldForCreateVikingdbCollectionInput;
import com.volcengine.vikingdb.runtime.core.ClientConfig;
import com.volcengine.vikingdb.runtime.core.auth.AuthWithAkSk;
import com.volcengine.vikingdb.runtime.enums.Scheme;
import com.volcengine.vikingdb.runtime.vector.service.VectorService;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named Multi-store factory for VikingDB. */
public final class VikingDbVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of(
            "host", "control-endpoint", "region", "access-key", "secret-key", "scheme");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "initialize-schema", "index-name", "project-name", "description",
            "metadata-fields", "scalar-index", "filter-validation-mode");
    private static final Set<String> INDEX_PARAM_KEYS = Set.of(
            "quantization", "hnsw-m", "hnsw-cef", "hnsw-sef",
            "diskann-m", "diskann-cef", "cache-ratio", "pq-code-ratio");
    private static final VectorStoreCapabilities BASE_CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.QUERY_TUNING, "1.0",
                    Map.of("api", VectorAdvancedSearchOperations.class.getName(),
                            "provider-api", VikingDbSearchOperations.class.getName(),
                            "modes", "DENSE,SPARSE,HYBRID")),
            VectorCapabilityDescriptor.supported(VectorCapability.SCORE_STAGES),
            VectorCapabilityDescriptor.supported(VectorCapability.PRECOMPUTED_VECTOR)));

    private final RerankService rerankService;
    private final ObservationRegistry observationRegistry;
    private final VectorStoreObservationConvention observationConvention;

    public VikingDbVectorProviderFactory(RerankService rerankService) {
        this(rerankService, ObservationRegistry.NOOP, null);
    }

    public VikingDbVectorProviderFactory(RerankService rerankService,
                                         ObservationRegistry observationRegistry,
                                         VectorStoreObservationConvention observationConvention) {
        this.rerankService = rerankService;
        this.observationRegistry = observationRegistry == null ? ObservationRegistry.NOOP : observationRegistry;
        this.observationConvention = observationConvention;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.VIKINGDB;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return BASE_CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "VikingDB connection settings");
        text(definition.settings(), "host", null);
        nullableText(definition.settings(), "control-endpoint");
        text(definition.settings(), "region", "cn-beijing");
        text(definition.settings(), "access-key", null);
        text(definition.settings(), "secret-key", null);
        scheme(definition.settings());
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("VikingDB named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        identifier(index.name(), "collection");
        identifier(text(index.additionalFields(), "index-name", index.name()), "index-name");
        vectorType(index.indexType());
        vectorDistance(index.metric());
        if (index.replicas() != 1) {
            throw new IllegalArgumentException("VikingDB replicas are not applied by the current adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "VikingDB index additional-fields");
        rejectUnknownKeys(index.indexParams(), INDEX_PARAM_KEYS, "VikingDB index-params");
        vectorOptions(index);
        boolean initializeSchema = booleanValue(index.additionalFields(), "initialize-schema", false);
        if (initializeSchema && nullableText(connection.settings(), "control-endpoint") == null) {
            throw new IllegalArgumentException(
                    "VikingDB initialize-schema=true requires connection control-endpoint");
        }
        metadataFields(index.additionalFields().get("metadata-fields"));
        List<String> scalarFields = scalarFields(index.additionalFields().get("scalar-index"));
        if (!metadataFields(index.additionalFields().get("metadata-fields")).keySet().containsAll(scalarFields)) {
            throw new IllegalArgumentException("Every VikingDB scalar-index field must be declared in metadata-fields");
        }
    }

    @Override
    public VectorStoreCapabilities capabilities(
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        requireProvider(connection);
        return storeCapabilities(connection, boundIndex(store));
    }

    @Override
    public Set<String> physicalResourceIdentities(
            VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        return Set.of("collection:" + boundIndex(store).name());
    }

    @Override
    public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        try {
            String accessKey = text(definition.settings(), "access-key", null);
            String secretKey = text(definition.settings(), "secret-key", null);
            String region = text(definition.settings(), "region", "cn-beijing");
            VectorService dataPlane = new VectorService(
                    scheme(definition.settings()),
                    text(definition.settings(), "host", null),
                    region,
                    new AuthWithAkSk(accessKey, secretKey),
                    ClientConfig.builder().build());
            String endpoint = nullableText(definition.settings(), "control-endpoint");
            VikingdbApi controlPlane = endpoint == null ? null : new VikingdbApi(
                    new ApiClient().setEndpoint(endpoint)
                            .setCredentials(Credentials.getCredentials(accessKey, secretKey))
                            .setRegion(region));
            return new VikingConnectionHandle(definition.id(), dataPlane, controlPlane);
        } catch (Exception exception) {
            throw new IllegalStateException("VikingDB client initialization failed", exception);
        }
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof VikingConnectionHandle vikingConnection)
                || connection.provider() != VectorProvider.VIKINGDB
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("VikingDB factory received an incompatible connection handle");
        }
        VectorIndexDefinition index = boundIndex(definition);
        validateStore(new VectorConnectionDefinition(
                connection.id(), provider(), Map.of(
                        "control-endpoint", vikingConnection.controlPlane() == null ? "" : "configured")), definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("VikingDB index dimension does not match the bound EmbeddingModel");
        }
        boolean initializeSchema = booleanValue(index.additionalFields(), "initialize-schema", false);
        VikingDbConfig config = storeConfig(index, initializeSchema);
        VikingDbVectorStoreFactory storeFactory = new VikingDbVectorStoreFactory(
                embeddingModel.model(), vikingConnection.dataPlane(), vikingConnection.controlPlane(),
                new TokenCountBatchingStrategy(), observationRegistry, observationConvention);
        VikingDbVectorStore vectorStore = storeFactory.create(VikingDbStoreSpec.builder()
                .collectionName(config.getCollectionName())
                .indexName(config.getIndexName())
                .embeddingDimension(index.dimension())
                .initializeSchema(initializeSchema)
                .projectName(config.getProjectName())
                .description(config.getDescription())
                .shardCount(config.getShardCount())
                .scalarIndex(config.getScalarIndex())
                .metadataFields(config.getMetadataFields())
                .filterValidationMode(config.getFilterValidationMode())
                .searchDefaults(config.searchDefaultsModel())
                .searchAdvanceDefaults(config.searchAdvanceDefaultsModel())
                .indexVectorOptions(config.indexVectorOptionsModel())
                .build());
        if (initializeSchema) vectorStore.afterPropertiesSet();
        VectorStoreCapabilities capabilities = storeCapabilities(
                new VectorConnectionDefinition(connection.id(), provider(),
                        vikingConnection.controlPlane() == null
                                ? Map.of() : Map.of("control-endpoint", "configured")), index);
        VectorFilterCompiler filterCompiler = new SpringAiVectorFilterCompiler();
        VikingDbVectorServiceImpl service = new VikingDbVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                config,
                definition.defaultIndex());
        service.setVectorProperties(storeProperties(definition, index));
        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(capabilities);
        VectorAdvancedSearchOperations advancedSearch = new VikingDbAdvancedSearchOperations(
                vectorStore, embeddingModel.model(), definition.id(), definition.defaultIndex(),
                VectorQueryDefaults.CORE_SAFE, definition.queryDefaults(),
                scoreSemantics(index.metric()));
        handle.capability(VectorAdvancedSearchOperations.class, advancedSearch)
                .capability(VikingDbSearchOperations.class, vectorStore)
                .capability(VikingDbDocumentOperations.class, vectorStore)
                .capability(VikingDbRerankOperations.class, vectorStore)
                .capability(VikingDbPermissionOperations.class, vectorStore)
                .capability(VectorScoreSemantics.class, scoreSemantics(index.metric()));
        vectorStore.getIndexOperations().ifPresent(operations -> handle.capability(VikingDbIndexOperations.class, operations));
        vectorStore.getCollectionOperations().ifPresent(operations -> handle.capability(VikingDbCollectionOperations.class, operations));
        if (capabilities.supports(VectorCapability.NATIVE_FILTER)) {
            service.setVectorFilterCompiler(filterCompiler);
            handle.capability(VectorFilterCompiler.class, filterCompiler);
        }
        return handle.build();
    }

    private static VikingDbConfig storeConfig(VectorIndexDefinition index, boolean initializeSchema) {
        VikingDbConfig config = new VikingDbConfig();
        config.setCollectionName(index.name());
        config.setIndexName(text(index.additionalFields(), "index-name", index.name()));
        config.setEmbeddingDimension(index.dimension());
        config.setInitializeSchema(initializeSchema);
        config.setProjectName(nullableText(index.additionalFields(), "project-name"));
        config.setDescription(nullableText(index.additionalFields(), "description"));
        config.setShardCount(index.shards());
        config.setMetadataFields(metadataFields(index.additionalFields().get("metadata-fields")));
        config.setScalarIndex(scalarFields(index.additionalFields().get("scalar-index")));
        config.setFilterValidationMode(filterValidationMode(index.additionalFields().get("filter-validation-mode")));
        copyVectorOptions(config.getIndexVectorOptions(), vectorOptions(index));
        return config;
    }

    private static VectorStoreCapabilities storeCapabilities(VectorConnectionDefinition connection,
                                                              VectorIndexDefinition index) {
        List<VectorCapabilityDescriptor> descriptors = new ArrayList<>(BASE_CAPABILITIES.descriptors());
        if (!scalarFields(index.additionalFields().get("scalar-index")).isEmpty()) {
            descriptors.add(new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall")));
            descriptors.add(new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0",
                    Map.of("filter-stage", "provider-recall", "schema", "declared-scalar-fields")));
        }
        if (nullableText(connection.settings(), "control-endpoint") != null) {
            descriptors.add(VectorCapabilityDescriptor.supported(VectorCapability.INDEX_LIFECYCLE));
        }
        return new VectorStoreCapabilities(descriptors);
    }

    private static VectorScoreSemantics scoreSemantics(String metric) {
        String lower = metric == null ? "cosine" : metric.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "cosine" -> VectorScoreSemantics.finalScore(
                    "vikingdb.provider.cosine", 0.0D, 1.0D, true, "vikingdb-cosine-score",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "ip", "dot" -> VectorScoreSemantics.finalScore(
                    "vikingdb.provider.inner-product", null, null, false, "vikingdb-raw-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            case "l2", "euclidean" -> VectorScoreSemantics.finalScore(
                    "vikingdb.provider.euclidean", 0.0D, 1.0D, true, "1.0/(1.0+distance)",
                    VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
            default -> VectorScoreSemantics.finalScore(
                    "vikingdb.provider", null, null, false, "vikingdb-raw-score",
                    VectorScoreThresholdKind.PROVIDER_RAW,
                    VectorScoreThresholdExecution.ADAPTER_NORMALIZED);
        };
    }

    private static cn.richie696.ai.vectorstore.vikingdb.model.VikingDbFilterValidationMode filterValidationMode(Object configured) {
        if (configured == null || String.valueOf(configured).isBlank()) {
            return cn.richie696.ai.vectorstore.vikingdb.model.VikingDbFilterValidationMode.DECLARED_FIELDS;
        }
        try {
            return cn.richie696.ai.vectorstore.vikingdb.model.VikingDbFilterValidationMode
                    .valueOf(String.valueOf(configured).trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported VikingDB filter-validation-mode: " + configured);
        }
    }

    private static VikingDbIndexVectorOptions vectorOptions(VectorIndexDefinition index) {
        VikingDbIndexVectorOptions.Builder builder = VikingDbIndexVectorOptions.builder()
                .type(vectorType(index.indexType()))
                .distance(vectorDistance(index.metric()))
                .quantization(quantization(index.indexParams().get("quantization")));
        integer(index.indexParams(), "hnsw-m").ifPresent(builder::hnswM);
        integer(index.indexParams(), "hnsw-cef").ifPresent(builder::hnswCef);
        integer(index.indexParams(), "hnsw-sef").ifPresent(builder::hnswSef);
        integer(index.indexParams(), "diskann-m").ifPresent(builder::diskannM);
        integer(index.indexParams(), "diskann-cef").ifPresent(builder::diskannCef);
        decimal(index.indexParams(), "cache-ratio").ifPresent(value -> builder.cacheRatio(value.floatValue()));
        decimal(index.indexParams(), "pq-code-ratio").ifPresent(value -> builder.pqCodeRatio(value.floatValue()));
        return builder.build();
    }

    private static void copyVectorOptions(VikingDbConfig.IndexVectorProperties target,
                                          VikingDbIndexVectorOptions source) {
        target.setType(source.type());
        target.setDistance(source.distance());
        target.setQuantization(source.quantization());
        target.setHnswM(source.hnswM());
        target.setHnswCef(source.hnswCef());
        target.setHnswSef(source.hnswSef());
        target.setDiskannM(source.diskannM());
        target.setDiskannCef(source.diskannCef());
        target.setCacheRatio(source.cacheRatio());
        target.setPqCodeRatio(source.pqCodeRatio());
    }

    private static VikingDbIndexVectorOptions.Type vectorType(String value) {
        try {
            return VikingDbIndexVectorOptions.Type.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unsupported VikingDB index type: " + value);
        }
    }

    private static VikingDbIndexVectorOptions.Distance vectorDistance(String value) {
        try {
            return VikingDbIndexVectorOptions.Distance.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unsupported VikingDB distance metric: " + value);
        }
    }

    private static VikingDbIndexVectorOptions.Quantization quantization(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return VikingDbIndexVectorOptions.Quantization.FLOAT;
        }
        try {
            return VikingDbIndexVectorOptions.Quantization.valueOf(String.valueOf(value).trim()
                    .replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported VikingDB quantization: " + value);
        }
    }

    private static java.util.Optional<Integer> integer(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Integer.valueOf(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("VikingDB index parameter must be an integer: " + key);
        }
    }

    private static java.util.Optional<Double> decimal(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return java.util.Optional.empty();
        try {
            return java.util.Optional.of(Double.valueOf(String.valueOf(value)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("VikingDB index parameter must be numeric: " + key);
        }
    }

    private static Map<String, FieldForCreateVikingdbCollectionInput.FieldTypeEnum> metadataFields(Object configured) {
        if (configured == null || String.valueOf(configured).isBlank()) return Map.of();
        Map<String, FieldForCreateVikingdbCollectionInput.FieldTypeEnum> result = new LinkedHashMap<>();
        for (String item : String.valueOf(configured).split(",")) {
            String[] parts = item.trim().split(":", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("VikingDB metadata-fields must use field:type entries");
            }
            var type = switch (parts[1].toLowerCase(Locale.ROOT)) {
                case "string" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.STRING;
                case "int64", "long" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.INT64;
                case "float32", "float" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.FLOAT32;
                case "bool", "boolean" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.BOOL;
                case "text" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.TEXT;
                case "list-string" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.LIST_STRING_;
                case "list-int64" -> FieldForCreateVikingdbCollectionInput.FieldTypeEnum.LIST_INT64_;
                default -> throw new IllegalArgumentException("unsupported VikingDB metadata field type: " + parts[1]);
            };
            if (result.putIfAbsent(parts[0], type) != null) {
                throw new IllegalArgumentException("duplicate VikingDB metadata field: " + parts[0]);
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> scalarFields(Object configured) {
        if (configured == null || String.valueOf(configured).isBlank()) return List.of();
        Set<String> result = new LinkedHashSet<>();
        for (String item : String.valueOf(configured).split(",")) {
            String field = item.trim();
            if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("invalid VikingDB scalar-index field: " + field);
            }
            if (!result.add(field)) throw new IllegalArgumentException("duplicate VikingDB scalar-index field: " + field);
        }
        return List.copyOf(result);
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException("VikingDB default index must identify the declared Store index");
        }
        return index;
    }

    private static VectorProperties storeProperties(VectorStoreDefinition store, VectorIndexDefinition index) {
        VectorProperties properties = new VectorProperties();
        properties.setDefaultIndex(store.defaultIndex());
        properties.setIndexes(Map.of(store.defaultIndex(), new VectorProperties.IndexConfig()
                .setName(index.name()).setDimension(index.dimension()).setMetric(index.metric())
                .setIndexType(index.indexType()).setReplicas(index.replicas()).setShards(index.shards())
                .setAdditionalFields(index.additionalFields()).setIndexParams(index.indexParams())));
        return properties;
    }

    private static Scheme scheme(Map<String, ?> settings) {
        String value = text(settings, "scheme", "HTTPS").toUpperCase(Locale.ROOT);
        try {
            return Scheme.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unsupported VikingDB scheme: " + value);
        }
    }

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.VIKINGDB) {
            throw new IllegalArgumentException("VikingDB factory cannot handle provider " + definition.provider());
        }
    }

    private static void rejectUnknownKeys(Map<String, ?> values, Set<String> allowed, String field) {
        Set<String> unknown = new LinkedHashSet<>(values.keySet());
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown " + field + ": " + unknown);
    }

    private static String text(Map<String, ?> values, String key, String fallback) {
        Object value = values.get(key);
        if (value == null) {
            if (fallback == null) throw new IllegalArgumentException("VikingDB setting is required: " + key);
            return fallback;
        }
        String result = String.valueOf(value).trim();
        if (result.isBlank()) {
            if (fallback == null) throw new IllegalArgumentException("VikingDB setting must not be blank: " + key);
            return fallback;
        }
        return result;
    }

    private static String nullableText(Map<String, ?> values, String key) {
        Object value = values.get(key);
        if (value == null) return null;
        String result = String.valueOf(value).trim();
        return result.isBlank() ? null : result;
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("VikingDB setting must be a boolean: " + key);
    }

    private static String identifier(String value, String field) {
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9_-]{0,127}")) {
            throw new IllegalArgumentException("invalid VikingDB " + field);
        }
        return value;
    }

    private record VikingConnectionHandle(
            VectorConnectionId id,
            VectorService dataPlane,
            VikingdbApi controlPlane) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.VIKINGDB;
        }

        @Override
        public void close() {
            // Current Volcengine VikingDB SDK clients expose no close lifecycle.
        }

        @Override
        public String toString() {
            return "VikingConnectionHandle[id=" + id + ", provider=VIKINGDB, config=<redacted>]";
        }
    }
}
