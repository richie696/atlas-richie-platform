package cn.richie696.component.vector.config;

import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbStoreSpec;
import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbVectorStore;
import cn.richie696.ai.vectorstore.tencentvectordb.TencentVectorDbVectorStoreFactory;
import cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbCollectionOperations;
import cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbDatabaseOperations;
import cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbDocumentOperations;
import cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbIndexOperations;
import cn.richie696.ai.vectorstore.tencentvectordb.api.TencentVectorDbSearchOperations;
import cn.richie696.component.ai.service.RerankService;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.SpringAiVectorFilterCompiler;
import cn.richie696.component.vector.filter.VectorFilterCompiler;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.service.SparseVectorizer;
import cn.richie696.component.vector.service.SparseVectorizerRegistry;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.impl.TencentVectorDbAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.impl.TencentVectorDbHybridVectorService;
import cn.richie696.component.vector.service.impl.TencentVectorDbStoreManagedVectorService;
import cn.richie696.component.vector.service.impl.StoreManagedVectorService;
import cn.richie696.component.vector.topology.*;
import com.tencent.tcvectordb.client.VectorDBClient;
import com.tencent.tcvectordb.model.Collection;
import com.tencent.tcvectordb.model.param.collection.CreateCollectionParam;
import com.tencent.tcvectordb.model.param.collection.FilterIndex;
import com.tencent.tcvectordb.model.param.collection.FilterIndexConfig;
import com.tencent.tcvectordb.model.param.collection.FieldType;
import com.tencent.tcvectordb.model.param.collection.IndexType;
import com.tencent.tcvectordb.model.param.collection.MetricType;
import com.tencent.tcvectordb.model.param.collection.SparseVectorIndex;
import com.tencent.tcvectordb.model.param.collection.VectorIndex;
import com.tencent.tcvectordb.model.param.database.ConnectParam;
import com.tencent.tcvectordb.model.param.enums.ReadConsistencyEnum;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Named multi-store adapter for Tencent Cloud VectorDB's Spring AI plugin. */
public final class TencentVectorDbProviderFactory implements VectorProviderFactory {
    private static final Set<String> CONNECTION_KEYS = Set.of("url", "username", "api-key", "timeout-seconds", "connect-timeout-seconds", "read-consistency");
    private static final Set<String> INDEX_FIELDS = Set.of("initialize-schema", "database-name", "description",
            "hybrid-enabled", "dense-field", "sparse-field", "sparse-vectorizer", "hybrid-candidate-limit", "hybrid-fallback-mode");
    private static final VectorStoreCapabilities CAPABILITIES = new VectorStoreCapabilities(List.of(
            new VectorCapabilityDescriptor(VectorCapability.NATIVE_FILTER, "1.0", Map.of("filter-stage", "provider-recall")),
            new VectorCapabilityDescriptor(VectorCapability.ACL_FILTER, "1.0", Map.of("filter-stage", "provider-recall"))));
    private final RerankService rerankService;
    private final SparseVectorizerRegistry sparseVectorizers;
    public TencentVectorDbProviderFactory(RerankService rerankService) {
        this(rerankService, new SparseVectorizerRegistry(Map.of()));
    }
    public TencentVectorDbProviderFactory(RerankService rerankService, SparseVectorizerRegistry sparseVectorizers) {
        this.rerankService = rerankService;
        this.sparseVectorizers = sparseVectorizers;
    }
    @Override public VectorProvider provider() { return VectorProvider.TENCENT_VECTORDB; }
    @Override public VectorStoreCapabilities adapterCapabilities() { return CAPABILITIES; }
    @Override public void validateConnection(VectorConnectionDefinition definition) {
        requireProvider(definition); rejectUnknown(definition.settings(), CONNECTION_KEYS, "Tencent VectorDB connection settings");
        text(definition.settings(), "url", null); text(definition.settings(), "username", null); text(definition.settings(), "api-key", null);
        positive(definition.settings(), "timeout-seconds", 10); positive(definition.settings(), "connect-timeout-seconds", 10); consistency(definition.settings());
    }
    @Override public void validateStore(VectorConnectionDefinition connection, VectorStoreDefinition store) {
        requireProvider(connection); if (store.indexes().size() != 1) throw new IllegalArgumentException("Tencent VectorDB named Store requires exactly one bound index");
        VectorIndexDefinition index = index(store); rejectUnknown(index.additionalFields(), INDEX_FIELDS, "Tencent VectorDB index additional-fields");
        if (!index.name().matches("[A-Za-z0-9_]{1,128}")) throw new IllegalArgumentException("invalid Tencent VectorDB collection name");
        metric(index.metric()); type(index.indexType()); booleanValue(index.additionalFields(), "initialize-schema", false); text(index.additionalFields(), "database-name", "spring_ai");
        HybridStoreOptions hybrid = HybridStoreOptions.fromAdditionalFields(index.additionalFields());
        if (hybrid.enabled()) {
            if (!"vector".equals(hybrid.denseField()) || !"sparse_vector".equals(hybrid.sparseField())) {
                throw new IllegalArgumentException("Tencent VectorDB hybrid Store uses the SDK default dense/sparse fields: vector and sparse_vector");
            }
            sparseVectorizers.require(hybrid.vectorizerBeanName());
        }
    }
    @Override public VectorStoreCapabilities capabilities(VectorConnectionDefinition connection, VectorStoreDefinition store) { requireProvider(connection); return capabilities(index(store)); }
    @Override public Set<String> physicalResourceIdentities(VectorConnectionDefinition connection, VectorStoreDefinition store) { VectorIndexDefinition index = index(store); return Set.of("collection:" + text(index.additionalFields(), "database-name", "spring_ai") + "." + index.name()); }
    @Override public VectorConnectionHandle openConnection(VectorConnectionDefinition definition) {
        validateConnection(definition); Map<String,Object> s = definition.settings();
        ConnectParam parameter = ConnectParam.newBuilder().withUrl(text(s,"url",null)).withUsername(text(s,"username",null)).withKey(text(s,"api-key",null))
                .withTimeout(positive(s,"timeout-seconds",10)).withConnectTimeout(positive(s,"connect-timeout-seconds",10)).build();
        return new Connection(definition.id(), new VectorDBClient(parameter, consistency(s)));
    }
    @Override public VectorStoreHandle createStore(VectorConnectionHandle connection, VectorStoreDefinition definition, VectorEmbeddingModelBinding embeddingModel) {
        if (!(connection instanceof Connection tencent) || connection.provider() != provider() || !connection.id().equals(definition.connectionId())) throw new IllegalArgumentException("Tencent VectorDB factory received incompatible connection");
        validateStore(new VectorConnectionDefinition(connection.id(), provider(), Map.of()), definition); VectorIndexDefinition index = index(definition);
        if (embeddingModel.dimensions() > 0 && embeddingModel.dimensions() != index.dimension()) throw new IllegalArgumentException("Tencent VectorDB index dimension does not match EmbeddingModel");
        boolean initialize = booleanValue(index.additionalFields(), "initialize-schema", false);
        HybridStoreOptions hybrid = HybridStoreOptions.fromAdditionalFields(index.additionalFields());
        String database = text(index.additionalFields(), "database-name", "spring_ai");
        if (hybrid.enabled()) {
            ensureHybridSchema(tencent.client(), database, index, hybrid, initialize);
        }
        TencentVectorDbVectorStore store = new TencentVectorDbVectorStoreFactory(embeddingModel.model(), tencent.client(), new TokenCountBatchingStrategy(), null, null).create(TencentVectorDbStoreSpec.builder()
                .databaseName(database).collectionName(index.name()).embeddingDimension(index.dimension()).initializeSchema(hybrid.enabled() ? false : initialize)
                .shardNum(index.shards()).replicaNum(index.replicas()).description(nullable(index.additionalFields(), "description")).indexType(type(index.indexType())).metricType(metric(index.metric())).build());
        if (initialize && !hybrid.enabled()) store.afterPropertiesSet();
        StoreManagedVectorService service = hybrid.enabled()
                ? new TencentVectorDbHybridVectorService(rerankService, store, embeddingModel.model(),
                Map.of(definition.defaultIndex(), index.name()), sparseVectorizers.require(hybrid.vectorizerBeanName()))
                : new TencentVectorDbStoreManagedVectorService(rerankService, store, embeddingModel.model(),
                Map.of(definition.defaultIndex(), index.name()));
        VectorFilterCompiler compiler = new SpringAiVectorFilterCompiler(); service.setVectorFilterCompiler(compiler); service.setVectorProperties(properties(definition,index));
        VectorStoreHandle.Builder handle = VectorStoreHandle.builder(definition, provider(), service).embeddingModel(embeddingModel)
                .storeCapabilities(capabilities(index)).capability(VectorFilterCompiler.class, compiler);
        handle.capability(TencentVectorDbDatabaseOperations.class, store)
                .capability(TencentVectorDbCollectionOperations.class, store)
                .capability(TencentVectorDbDocumentOperations.class, store)
                .capability(TencentVectorDbSearchOperations.class, store)
                .capability(TencentVectorDbIndexOperations.class, store);
        if (hybrid.enabled()) {
            SparseVectorizer sparse = sparseVectorizers.require(hybrid.vectorizerBeanName());
            handle.capability(VectorAclAwareHybridSearchOperations.class,
                    new TencentVectorDbAclAwareHybridSearchOperations(store, embeddingModel.model(), sparse, hybrid,
                            Map.of(definition.defaultIndex(), index.name())));
        }
        return handle.build();
    }
    private static VectorStoreCapabilities capabilities(VectorIndexDefinition index) {
        if (!HybridStoreOptions.fromAdditionalFields(index.additionalFields()).enabled()) return CAPABILITIES;
        java.util.ArrayList<VectorCapabilityDescriptor> descriptors = new java.util.ArrayList<>(CAPABILITIES.descriptors());
        descriptors.add(new VectorCapabilityDescriptor(VectorCapability.ACL_SAFE_HYBRID, "1.0",
                Map.of("filter-stage", "provider-recall", "execution", "native-first", "sparse-mode", "sparse-vector")));
        return new VectorStoreCapabilities(descriptors);
    }
    private static void ensureHybridSchema(VectorDBClient client, String database, VectorIndexDefinition index,
                                           HybridStoreOptions hybrid, boolean initialize) {
        if (initialize) {
            if (!client.IsExistsDatabase(database)) client.createDatabase(database);
            if (!client.IsExistsCollection(database, index.name())) {
                client.createCollection(database, CreateCollectionParam.newBuilder().withName(index.name())
                        .withShardNum(index.shards()).withReplicaNum(index.replicas())
                        .addField(new FilterIndex("id", FieldType.String, IndexType.PRIMARY_KEY))
                        .addField(new FilterIndex("content", FieldType.String, IndexType.FILTER))
                        .addField(new VectorIndex(hybrid.denseField(), index.dimension(), type(index.indexType()), metric(index.metric()),
                                type(index.indexType()) == IndexType.HNSW ? new com.tencent.tcvectordb.model.param.collection.HNSWParams(16, 200) : null))
                        .addField(new SparseVectorIndex(hybrid.sparseField(), IndexType.INVERTED, MetricType.IP))
                        .withFilterIndexConfig(FilterIndexConfig.newBuilder().withFilterAll(true)
                                .withFieldWithoutFilterIndex(List.of("content")).withMaxStrLen(256).build())
                        .build());
            }
        }
        Collection collection = client.describeCollection(database, index.name());
        boolean dense = collection.getIndexes().stream().anyMatch(field -> hybrid.denseField().equals(field.getFieldName()) && field.isVectorField());
        boolean sparse = collection.getIndexes().stream().anyMatch(field -> hybrid.sparseField().equals(field.getFieldName()) && field.isSparseVectorField());
        if (!dense || !sparse) {
            throw new IllegalStateException("Tencent VectorDB hybrid Store requires vector and sparse_vector indexes before ACL-safe hybrid can be enabled");
        }
    }
    private static IndexType type(String value) { try { return IndexType.valueOf(value.trim().toUpperCase(Locale.ROOT)); } catch (Exception e) { throw new IllegalArgumentException("unsupported Tencent VectorDB index type: " + value, e); } }
    private static MetricType metric(String value) { return switch (value.toLowerCase(Locale.ROOT)) { case "cosine" -> MetricType.COSINE; case "l2", "euclidean" -> MetricType.L2; case "ip", "dot" -> MetricType.IP; default -> throw new IllegalArgumentException("unsupported Tencent VectorDB metric: " + value); }; }
    private static ReadConsistencyEnum consistency(Map<String,?> values) { String value = text(values,"read-consistency","strong"); return switch (value.toLowerCase(Locale.ROOT)) { case "strong", "strong_consistency" -> ReadConsistencyEnum.STRONG_CONSISTENCY; case "eventual", "eventual_consistency" -> ReadConsistencyEnum.EVENTUAL_CONSISTENCY; default -> throw new IllegalArgumentException("unsupported Tencent VectorDB read-consistency: " + value); }; }
    private static VectorIndexDefinition index(VectorStoreDefinition store) { VectorIndexDefinition index = store.indexes().get(store.defaultIndex()); if (index == null) throw new IllegalArgumentException("Tencent VectorDB default index must identify the declared Store index"); return index; }
    private static void requireProvider(VectorConnectionDefinition d) { if (d.provider() != VectorProvider.TENCENT_VECTORDB) throw new IllegalArgumentException("Tencent VectorDB factory cannot handle provider " + d.provider()); }
    private static void rejectUnknown(Map<String,?> values, Set<String> allowed, String name) { Set<String> unknown = new java.util.LinkedHashSet<>(values.keySet()); unknown.removeAll(allowed); if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown " + name + ": " + unknown); }
    private static String text(Map<String,?> values, String key, String fallback) { Object value = values.get(key); if (value == null) return fallback; String text = String.valueOf(value).trim(); if (text.isBlank()) throw new IllegalArgumentException("Tencent VectorDB setting must not be blank: " + key); return text; }
    private static String nullable(Map<String,?> values, String key) { Object value = values.get(key); return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim(); }
    private static int positive(Map<String,?> values, String key, int fallback) { Object value = values.get(key); int result; try { result = value == null ? fallback : value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException e) { throw new IllegalArgumentException("Tencent VectorDB setting must be an integer: " + key,e); } if (result <= 0) throw new IllegalArgumentException("Tencent VectorDB setting must be positive: " + key); return result; }
    private static boolean booleanValue(Map<String,?> values,String key,boolean fallback) { Object value=values.get(key); if(value==null)return fallback; if(value instanceof Boolean b)return b; if("true".equalsIgnoreCase(String.valueOf(value)))return true; if("false".equalsIgnoreCase(String.valueOf(value)))return false; throw new IllegalArgumentException("Tencent VectorDB setting must be a boolean: "+key); }
    private static VectorProperties properties(VectorStoreDefinition store,VectorIndexDefinition index) { VectorProperties p=new VectorProperties(); p.setDefaultIndex(store.defaultIndex());p.setIndexes(Map.of(store.defaultIndex(),new VectorProperties.IndexConfig().setName(index.name()).setDimension(index.dimension()).setMetric(index.metric()).setIndexType(index.indexType()).setReplicas(index.replicas()).setShards(index.shards()).setAdditionalFields(index.additionalFields()).setIndexParams(index.indexParams())));return p; }
    private record Connection(VectorConnectionId id, VectorDBClient client) implements VectorConnectionHandle { @Override public VectorProvider provider() { return VectorProvider.TENCENT_VECTORDB; } @Override public void close() { client.close(); } @Override public String toString() { return "TencentVectorDbConnectionHandle[id="+id+", config=<redacted>]"; } }
}
