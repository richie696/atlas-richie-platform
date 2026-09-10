package cn.richie696.component.vector.config;

import cn.richie696.ai.vectorstore.dashvector.DashVectorStoreSpec;
import cn.richie696.ai.vectorstore.dashvector.DashVectorVectorStore;
import cn.richie696.ai.vectorstore.dashvector.DashVectorVectorStoreFactory;
import cn.richie696.ai.vectorstore.dashvector.api.DashVectorCollectionOperations;
import cn.richie696.ai.vectorstore.dashvector.api.DashVectorDocumentOperations;
import cn.richie696.ai.vectorstore.dashvector.api.DashVectorPartitionOperations;
import cn.richie696.ai.vectorstore.dashvector.api.DashVectorSearchOperations;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.service.impl.DashVectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.impl.DashVectorHybridVectorService;
import cn.richie696.component.vector.service.impl.DashVectorStoreManagedVectorService;
import cn.richie696.component.vector.service.impl.StoreManagedVectorService;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import cn.richie696.component.vector.service.SparseVectorizer;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.topology.*;
import com.aliyun.dashvector.DashVectorClient;
import com.aliyun.dashvector.DashVectorClientConfig;
import com.aliyun.dashvector.proto.CollectionInfo;
import com.aliyun.dashvector.proto.FieldType;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named multi-store adapter for DashVector's Spring AI 2.0 plugin. */
public final class DashVectorProviderFactory implements VectorProviderFactory {
    private static final Set<String> CONNECTION_KEYS = Set.of("endpoint", "api-key", "timeout-seconds");
    private static final Set<String> INDEX_FIELDS = Set.of("initialize-schema", "partition", "schema-initialization-timeout-seconds", "metadata-fields", "hybrid-enabled", "dense-field", "sparse-field", "sparse-vectorizer", "hybrid-candidate-limit", "hybrid-fallback-mode");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0", Map.of("filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0", Map.of("filter-stage", "provider-recall"))));
    private final RerankService rerankService;
    private final SparseVectorizerRegistry sparseVectorizers;
    public DashVectorProviderFactory(RerankService rerankService) { this(rerankService,new SparseVectorizerRegistry(Map.of())); }
    public DashVectorProviderFactory(RerankService rerankService,SparseVectorizerRegistry sparseVectorizers) { this.rerankService = rerankService; this.sparseVectorizers=sparseVectorizers; }
    @Override public VectorProvider provider() { return VectorProvider.DASHVECTOR; }
    @Override public VectorStoreCapabilities adapterCapabilities() { return CAPABILITIES; }
    @Override public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition); rejectUnknownKeys(definition.settings(), CONNECTION_KEYS, "DashVector connection settings");
        text(definition.settings(), "endpoint", null); text(definition.settings(), "api-key", null);
        if (integer(definition.settings(), "timeout-seconds", 30) <= 0) throw new IllegalArgumentException("DashVector timeout-seconds must be positive");
    }
    @Override public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection); if (store.indexes().size() != 1) throw new IllegalArgumentException("DashVector named Store requires exactly one bound index");
        VectorIndexDefinition index = boundIndex(store); rejectUnknownKeys(index.additionalFields(), INDEX_FIELDS, "DashVector index additional-fields");
        if (!index.name().matches("[A-Za-z0-9][A-Za-z0-9_-]{2,31}")) throw new IllegalArgumentException("DashVector collection name must use [A-Za-z0-9_-] and be 3-32 characters");
        metric(index.metric()); booleanValue(index.additionalFields(), "initialize-schema", false);
        text(index.additionalFields(), "partition", "default"); if (integer(index.additionalFields(), "schema-initialization-timeout-seconds", 120) <= 0) throw new IllegalArgumentException("DashVector schema initialization timeout must be positive");
        metadataFields(index.additionalFields().get("metadata-fields"));
        HybridStoreOptions hybrid = HybridStoreOptions.fromAdditionalFields(index.additionalFields());
        if (hybrid.enabled()) {
            if (!"ip".equalsIgnoreCase(index.metric()) && !"dot".equalsIgnoreCase(index.metric()) && !"dotproduct".equalsIgnoreCase(index.metric())) {
                throw new IllegalArgumentException("DashVector hybrid Store requires metric ip/dot because sparse_vector is supported only with dotproduct");
            }
            if (!"vector".equals(hybrid.denseField()) || !"sparse_vector".equals(hybrid.sparseField())) {
                throw new IllegalArgumentException("DashVector hybrid Store uses the SDK default dense/sparse fields: vector and sparse_vector");
            }
            sparseVectorizers.require(hybrid.vectorizerBeanName());
        }
    }
    @Override public VectorStoreCapabilities capabilities(VectorConnectionDefinition connection, VectorStoreDefinition store) { requireProvider(connection); return capabilities(boundIndex(store)); }
    @Override public Set<String> physicalResourceIdentities(VectorConnectionDefinition connection, VectorStoreDefinition store) { return Set.of("collection:" + boundIndex(store).name()); }
    @Override public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition);
        DashVectorClient client = new DashVectorClient(DashVectorClientConfig.builder().endpoint(sdkEndpoint(text(definition.settings(), "endpoint", null))).apiKey(text(definition.settings(), "api-key", null)).timeout((float) integer(definition.settings(), "timeout-seconds", 30)).build());
        return new Connection(definition.id(), client);
    }
    @Override public VectorStoreHandle createStore(VectorConnectionHandle connection, VectorStoreDefinition definition, VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof Connection dash) || connection.provider() != provider() || !connection.id().equals(definition.connectionId())) throw new IllegalArgumentException("DashVector factory received incompatible connection");
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition);
        VectorIndexDefinition index = boundIndex(definition);
        HybridStoreOptions hybrid=HybridStoreOptions.fromAdditionalFields(index.additionalFields());
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) throw new IllegalArgumentException("DashVector index dimension does not match EmbeddingModel");
        Map<String, FieldType> fields = metadataFields(index.additionalFields().get("metadata-fields"));
        DashVectorVectorStore store = new DashVectorVectorStoreFactory(embeddingModel.model(), dash.client(), new TokenCountBatchingStrategy(), null, null).create(DashVectorStoreSpec.builder()
                .collectionName(index.name()).embeddingDimension(index.dimension()).initializeSchema(booleanValue(index.additionalFields(), "initialize-schema", false))
                .partition(text(index.additionalFields(), "partition", "default")).metric(metric(index.metric()))
                .schemaInitializationTimeoutSeconds(integer(index.additionalFields(), "schema-initialization-timeout-seconds", 120)).metadataFields(fields).build());
        if (booleanValue(index.additionalFields(), "initialize-schema", false)) store.afterPropertiesSet();
        StoreManagedVectorService service = hybrid.enabled()
                ? new DashVectorHybridVectorService(rerankService, store, embeddingModel.model(),
                Map.of(definition.defaultIndex(), index.name()), sparseVectorizers.require(hybrid.vectorizerBeanName()),
                text(index.additionalFields(), "partition", "default"))
                : new DashVectorStoreManagedVectorService(rerankService, store, embeddingModel.model(),
                Map.of(definition.defaultIndex(), index.name()));
        VectorFilterCompiler compiler = new SpringAiVectorFilterCompiler(); service.setVectorFilterCompiler(compiler); service.setVectorProperties(storeProperties(definition, index));
        VectorStoreHandle.Builder handle=VectorStoreHandle.builder(definition, provider(), service).embeddingModel(embeddingModel).storeCapabilities(capabilities(index))
                .capability(VectorFilterCompiler.class, compiler);
        handle.capability(DashVectorCollectionOperations.class, store)
                .capability(DashVectorDocumentOperations.class, store)
                .capability(DashVectorPartitionOperations.class, store)
                .capability(DashVectorSearchOperations.class, store);
        if(hybrid.enabled()){SparseVectorizer sparse=sparseVectorizers.require(hybrid.vectorizerBeanName());handle.capability(VectorAclAwareHybridSearchOperations.class,new DashVectorAclAwareHybridSearchOperations(store,embeddingModel.model(),sparse,hybrid,Map.of(definition.defaultIndex(),index.name())));}
        return handle.build();
    }
    private static VectorStoreCapabilities capabilities(VectorIndexDefinition index){if(!HybridStoreOptions.fromAdditionalFields(index.additionalFields()).enabled())return CAPABILITIES;java.util.ArrayList<VectorCapabilityDescriptor> d=new java.util.ArrayList<>(CAPABILITIES.descriptors());d.add(new VectorCapabilityDescriptor(VectorCapability.ACL_SAFE_HYBRID,"1.0",Map.of("filter-stage","provider-recall","execution","native","sparse-mode","dashvector-sparse")));return new VectorStoreCapabilities(d);}
    private static CollectionInfo.Metric metric(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "cosine" -> CollectionInfo.Metric.cosine; case "l2", "euclidean" -> CollectionInfo.Metric.euclidean; case "ip", "dot" -> CollectionInfo.Metric.dotproduct; default -> throw new IllegalArgumentException("unsupported DashVector metric: " + value); }; }
    private static Map<String, FieldType> metadataFields(Object raw) { if (raw == null || String.valueOf(raw).isBlank()) return Map.of(); Map<String, FieldType> result = new LinkedHashMap<>(); for (String item : String.valueOf(raw).split(";")) { String[] parts = item.trim().split(":", -1); if (parts.length != 2 || parts[0].isBlank()) throw new IllegalArgumentException("DashVector metadata-fields entries must use field:TYPE"); try { result.put(parts[0].trim(), FieldType.valueOf(parts[1].trim().toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("unsupported DashVector metadata field type: " + parts[1], e); } } return Map.copyOf(result); }
    private static VectorIndexDefinition boundIndex(VectorStoreDefinition store) { VectorIndexDefinition index = store.indexes().get(store.defaultIndex()); if (index == null) throw new IllegalArgumentException("DashVector default index must identify the declared Store index"); return index; }
    private static void requireProvider(VectorConnectionDefinition definition) { if (definition.provider() != VectorProvider.DASHVECTOR) throw new IllegalArgumentException("DashVector factory cannot handle provider " + definition.provider()); }
    private static void rejectUnknownKeys(Map<String, ?> values, Set<String> allowed, String field) { Set<String> unknown = new java.util.LinkedHashSet<>(values.keySet()); unknown.removeAll(allowed); if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown " + field + ": " + unknown); }
    private static String text(Map<String, ?> values, String key, String fallback) { Object value = values.get(key); if (value == null) return fallback; String text = String.valueOf(value).trim(); if (text.isBlank()) throw new IllegalArgumentException("DashVector setting must not be blank: " + key); return text; }
    /** DashVector SDK accepts a gRPC authority, while application configuration conventionally uses an HTTPS URL. */
    static String sdkEndpoint(String endpoint) {
        String normalized = endpoint.replaceFirst("^https?://", "").replaceFirst("/$", "");
        if (normalized.isBlank() || normalized.contains("/")) {
            throw new IllegalArgumentException("DashVector endpoint must be a host[:port] or an HTTP(S) URL without a path");
        }
        return normalized;
    }
    private static int integer(Map<String, ?> values, String key, int fallback) { Object value = values.get(key); if (value == null) return fallback; try { return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim()); } catch (NumberFormatException e) { throw new IllegalArgumentException("DashVector setting must be an integer: " + key, e); } }
    private static boolean booleanValue(Map<String, ?> values, String key, boolean fallback) { Object value = values.get(key); if (value == null) return fallback; if (value instanceof Boolean bool) return bool; if ("true".equalsIgnoreCase(String.valueOf(value))) return true; if ("false".equalsIgnoreCase(String.valueOf(value))) return false; throw new IllegalArgumentException("DashVector setting must be a boolean: " + key); }
    private static VectorProperties storeProperties(VectorStoreDefinition store, VectorIndexDefinition index) { VectorProperties properties = new VectorProperties(); properties.setDefaultIndex(store.defaultIndex()); properties.setIndexes(Map.of(store.defaultIndex(), new VectorProperties.IndexConfig().setName(index.name()).setDimension(index.dimension()).setMetric(index.metric()).setIndexType(index.indexType()).setReplicas(index.replicas()).setShards(index.shards()).setAdditionalFields(index.additionalFields()).setIndexParams(index.indexParams()))); return properties; }
    private record Connection(VectorConnectionId id, DashVectorClient client) implements VectorConnectionHandle { @Override public VectorProvider provider() { return VectorProvider.DASHVECTOR; } @Override public void close() { client.close(); } @Override public String toString() { return "DashVectorConnectionHandle[id=" + id + ", config=<redacted>]"; } }
}
