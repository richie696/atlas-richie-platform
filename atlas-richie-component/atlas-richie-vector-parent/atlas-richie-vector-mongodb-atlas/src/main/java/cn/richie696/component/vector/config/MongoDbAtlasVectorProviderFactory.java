/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.VectorIndexLifecycleOperations;
import cn.richie696.component.vector.service.VectorRecordReadOperations;
import cn.richie696.component.vector.service.impl.MongoDbAtlasVectorServiceImpl;
import cn.richie696.component.vector.topology.VectorCapability;
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
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.ai.vectorstore.mongodb.atlas.MongoDBAtlasVectorStore;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/** Named Multi-store factory for MongoDB Atlas Vector Search. */
public final class MongoDbAtlasVectorProviderFactory implements VectorProviderFactory {

    private static final Set<String> CONNECTION_KEYS = Set.of("connection-string", "database");
    private static final Set<String> INDEX_ADDITIONAL_FIELDS = Set.of(
            "initialize-schema", "vector-index-name", "filter-metadata-fields");
    private static final Set<String> INDEX_PARAM_KEYS = Set.of("numCandidates");
    private static final VectorStoreCapabilities BASE_CAPABILITIES = VectorStoreCapabilities.of(
            VectorCapability.SCORE_STAGES,
            VectorCapability.INDEX_LIFECYCLE);
    private static final VectorStoreCapabilities ADAPTER_CAPABILITIES = VectorStoreCapabilities.of(
            VectorCapability.NATIVE_FILTER,
            VectorCapability.ACL_FILTER,
            VectorCapability.SCORE_STAGES,
            VectorCapability.INDEX_LIFECYCLE);

    private final RerankService rerankService;
    private final Function<String, MongoClient> clientFactory;

    public MongoDbAtlasVectorProviderFactory(RerankService rerankService) {
        this(rerankService, MongoClients::create);
    }

    MongoDbAtlasVectorProviderFactory(
            RerankService rerankService,
            Function<String, MongoClient> clientFactory) {
        this.rerankService = rerankService;
        this.clientFactory = clientFactory;
    }

    @Override
    public VectorProvider provider() {
        return VectorProvider.MONGODB;
    }

    @Override
    public VectorStoreCapabilities adapterCapabilities() {
        return ADAPTER_CAPABILITIES;
    }

    @Override
    public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition);
        rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "MongoDB Atlas connection settings");
        String connectionString = text(definition.settings(), "connection-string", null);
        if (!connectionString.startsWith("mongodb://") && !connectionString.startsWith("mongodb+srv://")) {
            throw new IllegalArgumentException("MongoDB Atlas connection-string must use mongodb or mongodb+srv");
        }
        mongoIdentifier(text(definition.settings(), "database", null), "database");
    }

    @Override
    public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection);
        if (store.indexes().size() != 1) {
            throw new IllegalArgumentException("MongoDB Atlas named Store requires exactly one bound index");
        }
        VectorIndexDefinition index = boundIndex(store);
        mongoIdentifier(index.name(), "collection");
        if (!"hnsw".equalsIgnoreCase(index.indexType())) {
            throw new IllegalArgumentException("MongoDB Atlas named Store currently supports hnsw indexes only");
        }
        if (!"cosine".equalsIgnoreCase(index.metric())) {
            throw new IllegalArgumentException(
                    "MongoDB Atlas Spring AI data plane currently supports cosine metric only");
        }
        if (index.replicas() != 1 || index.shards() != 1) {
            throw new IllegalArgumentException(
                    "MongoDB Atlas replicas/shards are deployment settings and are not applied by this adapter");
        }
        rejectUnknownKeys(index.additionalFields(), INDEX_ADDITIONAL_FIELDS,
                "MongoDB Atlas index additional-fields");
        rejectUnknownKeys(index.indexParams(), INDEX_PARAM_KEYS, "MongoDB Atlas index-params");
        booleanValue(index.additionalFields(), "initialize-schema", false);
        mongoIdentifier(text(index.additionalFields(), "vector-index-name", "vector_index"),
                "vector-index-name");
        metadataFields(index.additionalFields().get("filter-metadata-fields"));
        positiveInteger(index.indexParams(), "numCandidates", 200);
    }

    @Override
    public VectorStoreCapabilities capabilities(
            VectorConnectionDefinition connection,
            VectorStoreDefinition store) {
        requireProvider(connection);
        return storeCapabilities(boundIndex(store));
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
        MongoClient client = clientFactory.apply(text(definition.settings(), "connection-string", null));
        MongoTemplate template = new MongoTemplate(client, text(definition.settings(), "database", null));
        return new MongoConnectionHandle(definition.id(), client, template);
    }

    @Override
    public VectorStoreHandle createStore(
            VectorConnectionHandle connection,
            VectorStoreDefinition definition,
            VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof MongoConnectionHandle mongoConnection)
                || connection.provider() != VectorProvider.MONGODB
                || !connection.id().equals(definition.connectionId())) {
            throw new IllegalArgumentException("MongoDB Atlas factory received an incompatible connection handle");
        }
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition);
        VectorIndexDefinition index = boundIndex(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) {
            throw new IllegalArgumentException("MongoDB Atlas index dimension does not match the bound EmbeddingModel");
        }
        String vectorIndex = text(index.additionalFields(), "vector-index-name", "vector_index");
        List<String> filterFields = metadataFields(index.additionalFields().get("filter-metadata-fields"));
        boolean initializeSchema = booleanValue(index.additionalFields(), "initialize-schema", false);
        MongoDBAtlasVectorStore.Builder builder = MongoDBAtlasVectorStore
                .builder(mongoConnection.template(), embeddingModel.model())
                .collectionName(index.name())
                .vectorIndexName(vectorIndex)
                .pathName("embedding")
                .numCandidates(integer(index.indexParams(), "numCandidates", 200))
                .initializeSchema(initializeSchema);
        if (!filterFields.isEmpty()) builder.metadataFieldsToFilter(filterFields);
        MongoDBAtlasVectorStore vectorStore = builder.build();
        if (initializeSchema) {
            try {
                vectorStore.afterPropertiesSet();
            } catch (Exception exception) {
                throw new IllegalStateException("MongoDB Atlas named Store schema initialization failed", exception);
            }
        }
        VectorFilterCompiler filterCompiler = new SpringAiVectorFilterCompiler();
        VectorStoreCapabilities capabilities = storeCapabilities(index);
        MongoDbAtlasVectorServiceImpl service = new MongoDbAtlasVectorServiceImpl(
                rerankService,
                vectorStore,
                embeddingModel.model(),
                mongoConnection.template(),
                Map.of(definition.defaultIndex(), index.name()),
                Map.of(definition.defaultIndex(), vectorIndex));
        service.setVectorProperties(storeProperties(definition, index));
        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service)
                .embeddingModel(embeddingModel)
                .storeCapabilities(capabilities)
                .capability(VectorScoreSemantics.class, VectorScoreSemantics.finalScore(
                        "mongodb-atlas.provider.cosine", 0.0D, 1.0D, true, "atlas-normalized-score",
                        VectorScoreThresholdKind.NORMALIZED_RELEVANCE,
                        VectorScoreThresholdExecution.ADAPTER_NORMALIZED))
                .capability(VectorRecordReadOperations.class, service)
                .capability(VectorIndexLifecycleOperations.class, service);
        if (capabilities.supports(VectorCapability.NATIVE_FILTER)) {
            service.setVectorFilterCompiler(filterCompiler);
            handle.capability(VectorFilterCompiler.class, filterCompiler);
        }
        return handle.build();
    }

    private static VectorStoreCapabilities storeCapabilities(VectorIndexDefinition index) {
        return metadataFields(index.additionalFields().get("filter-metadata-fields")).isEmpty()
                ? BASE_CAPABILITIES
                : VectorStoreCapabilities.of(
                        VectorCapability.NATIVE_FILTER,
                        VectorCapability.ACL_FILTER,
                        VectorCapability.SCORE_STAGES,
                        VectorCapability.INDEX_LIFECYCLE);
    }

    private static List<String> metadataFields(Object configured) {
        if (configured == null) return List.of();
        String value = String.valueOf(configured).trim();
        if (value.isBlank()) return List.of();
        List<String> fields = Arrays.stream(value.split(","))
                .map(String::trim)
                .peek(field -> {
                    if (!field.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                        throw new IllegalArgumentException("invalid MongoDB Atlas filter metadata field: " + field);
                    }
                })
                .distinct()
                .toList();
        if (fields.size() != value.split(",").length) {
            throw new IllegalArgumentException("duplicate MongoDB Atlas filter metadata field");
        }
        return fields;
    }

    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) {
        VectorIndexDefinition index = store.indexes().get(store.defaultIndex());
        if (index == null) {
            throw new IllegalArgumentException(
                    "MongoDB Atlas default index must identify the declared Store index");
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

    private static void requireProvider(VectorConnectionDefinition definition) {
        if (definition.provider() != VectorProvider.MONGODB) {
            throw new IllegalArgumentException(
                    "MongoDB Atlas factory cannot handle provider " + definition.provider());
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
            if (fallback == null) throw new IllegalArgumentException("MongoDB Atlas setting is required: " + key);
            return fallback;
        }
        String result = String.valueOf(value).trim();
        if (result.isBlank()) throw new IllegalArgumentException("MongoDB Atlas setting must not be blank: " + key);
        return result;
    }

    private static int integer(Map<String, ?> values, String key, int fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("MongoDB Atlas setting must be an integer: " + key);
        }
    }

    private static int positiveInteger(Map<String, ?> values, String key, int fallback) {
        int value = integer(values, key, fallback);
        if (value <= 0) {
            throw new IllegalArgumentException("MongoDB Atlas setting must be greater than zero: " + key);
        }
        return value;
    }

    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) {
        Object value = values.get(key);
        if (value == null) return fallback;
        if (value instanceof Boolean bool) return bool;
        if ("true".equalsIgnoreCase(String.valueOf(value))) return true;
        if ("false".equalsIgnoreCase(String.valueOf(value))) return false;
        throw new IllegalArgumentException("MongoDB Atlas setting must be a boolean: " + key);
    }

    private static String mongoIdentifier(String value, String field) {
        if (!value.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,119}") || value.contains("..")) {
            throw new IllegalArgumentException("invalid MongoDB Atlas " + field);
        }
        return value;
    }

    private record MongoConnectionHandle(
            VectorConnectionId id,
            MongoClient client,
            MongoTemplate template) implements VectorConnectionHandle {

        @Override
        public VectorProvider provider() {
            return VectorProvider.MONGODB;
        }

        @Override
        public void close() {
            client.close();
        }

        @Override
        public String toString() {
            return "MongoConnectionHandle[id=" + id + ", provider=MONGODB, config=<redacted>]";
        }
    }
}
