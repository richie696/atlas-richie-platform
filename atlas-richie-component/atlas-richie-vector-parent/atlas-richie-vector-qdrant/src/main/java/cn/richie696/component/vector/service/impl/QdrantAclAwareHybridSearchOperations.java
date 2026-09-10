package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.filter.QdrantGrpcFilterMapper;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorSearchResult;
import cn.richie696.component.vector.service.SparseVector;
import cn.richie696.component.vector.service.SparseVectorizer;
import cn.richie696.component.vector.service.VectorAclAwareHybridSearchOperations;
import cn.richie696.component.vector.service.HybridSearchExecutionMode;
import cn.richie696.component.vector.service.support.AclSafeHybridSearchFallback;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Qdrant ACL-safe hybrid implementation using two native, pre-filtered recalls and Core RRF.
 * Qdrant's legacy SearchPoints protocol has no stable server-side dense+sparse fusion contract
 * in the supported SDK line, so this adapter intentionally uses the provider-neutral fallback
 * only after the same protobuf filter has been attached to both candidate recalls.
 */
public final class QdrantAclAwareHybridSearchOperations implements VectorAclAwareHybridSearchOperations, HybridSearchExecutionMode {
    private static final int WAIT_TIMEOUT_SECONDS = 5;
    private final QdrantClient client;
    private final EmbeddingModel embeddingModel;
    private final SparseVectorizer sparseVectorizer;
    private final HybridStoreOptions storeOptions;
    private final QdrantGrpcFilterMapper filterMapper;

    public QdrantAclAwareHybridSearchOperations(QdrantClient client, EmbeddingModel embeddingModel,
                                                SparseVectorizer sparseVectorizer, HybridStoreOptions storeOptions,
                                                QdrantGrpcFilterMapper filterMapper) {
        this.client = client;
        this.embeddingModel = embeddingModel;
        this.sparseVectorizer = sparseVectorizer;
        this.storeOptions = storeOptions;
        this.filterMapper = filterMapper;
        if (!storeOptions.enabled()) throw new IllegalArgumentException("Qdrant hybrid Store options must be enabled");
    }

    @Override public String hybridExecutionMode() { return "core-rrf"; }

    @Override
    public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                                 int limit, HybridSearchOptions options) {
        throw new UnsupportedOperationException("Qdrant ACL-safe hybrid search requires an explicit structured ACL filter");
    }

    @Override
    public List<VectorSearchResult> hybridSearch(String indexName, String text, String keywordQuery,
                                                 int limit, HybridSearchOptions options, VectorFilter filter) {
        if (indexName == null || indexName.isBlank()) throw new IllegalArgumentException("indexName must not be blank");
        if (limit <= 0) throw new IllegalArgumentException("hybrid search limit must be greater than zero");
        if (filter == null) throw new IllegalArgumentException("ACL filter must not be null");
        if ((text == null || text.isBlank()) && (keywordQuery == null || keywordQuery.isBlank())) {
            throw new IllegalArgumentException("text and keywordQuery must not both be blank");
        }
        SearchOptions searchOptions = options == null || options.getSearchOptions() == null
                ? SearchOptions.builder().build() : options.getSearchOptions();
        if (searchOptions.getFilter() != null && !searchOptions.getFilter().equals(filter)) {
            throw new IllegalArgumentException("ACL filter conflicts with hybrid search options filter");
        }
        Common.Filter grpcFilter = filterMapper.map(filter);
        int candidates = Math.max(limit, storeOptions.candidateLimit());
        List<VectorSearchResult> dense = text == null || text.isBlank() ? List.of()
                : dense(indexName, text, candidates, grpcFilter, searchOptions);
        List<VectorSearchResult> sparse = keywordQuery == null || keywordQuery.isBlank() ? List.of()
                : sparse(indexName, keywordQuery, candidates, grpcFilter, searchOptions);
        return AclSafeHybridSearchFallback.fuse(dense, sparse, options, limit);
    }

    private List<VectorSearchResult> dense(String collection, String text, int limit, Common.Filter filter,
                                           SearchOptions options) {
        float[] vector = embeddingModel.embed(text);
        if (vector == null || vector.length == 0) throw new IllegalStateException("Qdrant dense embedding must not be empty");
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) values.add(value);
        return search(Points.SearchPoints.newBuilder().setCollectionName(collection).setVectorName(storeOptions.denseField())
                .addAllVector(values).setFilter(filter).setLimit(limit).setWithPayload(payload()).build(), options);
    }

    private List<VectorSearchResult> sparse(String collection, String text, int limit, Common.Filter filter,
                                            SearchOptions options) {
        SparseVector vector = sparseVectorizer.encode(text);
        Points.SearchPoints.Builder request = Points.SearchPoints.newBuilder().setCollectionName(collection)
                .setVectorName(storeOptions.sparseField()).setFilter(filter).setLimit(limit).setWithPayload(payload());
        List<Map.Entry<Long, Float>> coordinates = vector.coordinates().entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList();
        Points.SparseIndices.Builder indices = Points.SparseIndices.newBuilder();
        for (Map.Entry<Long, Float> coordinate : coordinates) {
            if (coordinate.getKey() > Integer.MAX_VALUE) throw new IllegalArgumentException("Qdrant sparse token id exceeds 32-bit index range");
            request.addVector(coordinate.getValue());
            indices.addData(coordinate.getKey().intValue());
        }
        request.setSparseIndices(indices);
        return search(request.build(), options);
    }

    private static Points.WithPayloadSelector payload() {
        return Points.WithPayloadSelector.newBuilder().setEnable(true).build();
    }

    private List<VectorSearchResult> search(Points.SearchPoints request, SearchOptions options) {
        try {
            List<Points.ScoredPoint> points = client.searchAsync(request).get(WAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            double threshold = options.getMinScore() == null ? 0D : options.getMinScore();
            return points.stream().filter(point -> point.getScore() >= threshold).map(this::result).toList();
        } catch (Exception exception) {
            throw new IllegalStateException("Qdrant ACL-filtered hybrid candidate recall failed", exception);
        }
    }

    private VectorSearchResult result(Points.ScoredPoint point) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        point.getPayloadMap().forEach((key, value) -> metadata.put(key, value(value)));
        Object content = metadata.remove("content");
        return VectorSearchResult.of(point.getId().getUuid(), content == null ? "" : String.valueOf(content),
                (double) point.getScore()).setMetadata(metadata);
    }

    private static Object value(JsonWithInt.Value value) {
        return switch (value.getKindCase()) {
            case STRING_VALUE -> value.getStringValue();
            case INTEGER_VALUE -> value.getIntegerValue();
            case DOUBLE_VALUE -> value.getDoubleValue();
            case BOOL_VALUE -> value.getBoolValue();
            case LIST_VALUE -> value.getListValue().getValuesList().stream().map(QdrantAclAwareHybridSearchOperations::value).toList();
            case STRUCT_VALUE -> value.getStructValue().getFieldsMap().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, entry -> value(entry.getValue())));
            default -> null;
        };
    }
}
