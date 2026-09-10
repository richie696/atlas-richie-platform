package cn.richie696.component.vector.config;

import cn.richie696.component.vector.filter.QdrantGrpcFilterMapper;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.model.VectorRecord;
import cn.richie696.component.vector.service.SparseVector;
import cn.richie696.component.vector.service.SparseVectorizer;
import cn.richie696.component.vector.service.impl.QdRantVectorServiceImpl;
import cn.richie696.component.vector.service.impl.QdrantAclAwareHybridSearchOperations;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** True service E2E: both Qdrant candidate recalls receive the same ACL protobuf filter. */
@EnabledIfEnvironmentVariable(named = "VECTOR_QDRANT_IT_RUN", matches = "true")
class QdrantAclSafeHybridLiveIT {
    @Test
    void keepsDeniedSparseWinnerOutOfAclSafeHybridResultsAndDeletesBothVectors() throws Exception {
        String collection = "atlas_acl_hybrid_it_" + UUID.randomUUID().toString().replace("-", "");
        HybridStoreOptions options = new HybridStoreOptions(true, "dense", "sparse", "testSparse", 10,
                HybridStoreOptions.FallbackMode.CORE_RRF);
        try (QdrantClient client = new QdrantClient(QdrantGrpcClient.newBuilder("127.0.0.1", 6334, false).build())) {
            createCollection(client, collection, options);
            try {
                EmbeddingModel embedding = embedding();
                SparseVectorizer sparse = text -> text.contains("rare")
                        ? new SparseVector(Map.of(9L, 1F)) : new SparseVector(Map.of(1L, 1F));
                QdrantVectorStore store = QdrantVectorStore.builder(client, embedding).collectionName(collection).build();
                QdRantVectorServiceImpl service = new QdRantVectorServiceImpl(
                        null, store, embedding, client, Map.of("documents", collection), options, sparse);
                QdrantAclAwareHybridSearchOperations hybrid = new QdrantAclAwareHybridSearchOperations(
                        client, embedding, sparse, options, new QdrantGrpcFilterMapper());
                String allowed = UUID.randomUUID().toString();
                String denied = UUID.randomUUID().toString();
                service.upsert(VectorRecord.text("documents", allowed, "ordinary allowed")
                        .setMetadata(Map.of("tenantId", "tenant-a")));
                service.upsert(VectorRecord.text("documents", denied, "rare denied")
                        .setMetadata(Map.of("tenantId", "tenant-b")));

                var results = hybrid.hybridSearch(collection, "ordinary", "rare", 5,
                        HybridSearchOptions.builder().vectorWeight(0.3D).keywordWeight(0.7D).build(),
                        VectorFilter.eq("tenantId", "tenant-a"));
                assertThat(results).extracting(result -> result.getId()).containsExactly(allowed);
                assertThat(results).noneMatch(result -> denied.equals(result.getId()));

                service.deleteById("documents", allowed);
                assertThat(hybrid.hybridSearch(collection, "ordinary", "ordinary", 5, null,
                        VectorFilter.eq("tenantId", "tenant-a"))).isEmpty();
            } finally {
                client.deleteCollectionAsync(collection).get(10, TimeUnit.SECONDS);
            }
        }
    }

    private static void createCollection(QdrantClient client, String collection, HybridStoreOptions options) throws Exception {
        Collections.VectorParams dense = Collections.VectorParams.newBuilder().setSize(4)
                .setDistance(Collections.Distance.Cosine).build();
        client.createCollectionAsync(Collections.CreateCollection.newBuilder().setCollectionName(collection)
                        .setVectorsConfig(Collections.VectorsConfig.newBuilder().setParamsMap(
                                Collections.VectorParamsMap.newBuilder().putMap(options.denseField(), dense)))
                        .setSparseVectorsConfig(Collections.SparseVectorConfig.newBuilder().putMap(options.sparseField(),
                                Collections.SparseVectorParams.newBuilder().build()))
                        .build())
                .get(10, TimeUnit.SECONDS);
    }

    private static EmbeddingModel embedding() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class}, (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("embed".equals(method.getName()) && arguments[0] instanceof List<?> values)
                        return values.stream().map(ignored -> new float[]{1F, 0F, 0F, 0F}).toList();
                    if ("embed".equals(method.getName())) return new float[]{1F, 0F, 0F, 0F};
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }
}
