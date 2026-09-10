package cn.richie696.component.vector.config;

import cn.richie696.component.vector.filter.RedisSearchAclFilterCompiler;
import cn.richie696.component.vector.model.HybridSearchOptions;
import cn.richie696.component.vector.model.HybridStoreOptions;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.service.impl.RedisAclAwareHybridSearchOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;
import redis.clients.jedis.RedisClient;
import redis.clients.jedis.json.Path;
import redis.clients.jedis.search.FTCreateParams;
import redis.clients.jedis.search.IndexDataType;
import redis.clients.jedis.search.schemafields.TagField;
import redis.clients.jedis.search.schemafields.TextField;
import redis.clients.jedis.search.schemafields.VectorField;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** True Redis Stack E2E: both KNN and full-text candidate recalls receive the same ACL predicate. */
@EnabledIfEnvironmentVariable(named = "VECTOR_REDIS_IT_RUN", matches = "true")
class RedisAclSafeHybridLiveIT {
    @Test
    void excludesDeniedFullTextWinnerAndCleansUpRecords() {
        String index = "atlas_acl_hybrid_it_" + UUID.randomUUID().toString().replace("-", "");
        try (RedisClient client = RedisClient.builder().hostAndPort("127.0.0.1", 16380).build()) {
            createIndex(client, index);
            String allowed = UUID.randomUUID().toString();
            String denied = UUID.randomUUID().toString();
            try {
                put(client, index + ":" + allowed, "ordinary permitted", "tenant-a", List.of(1F, 0F, 0F, 0F));
                put(client, index + ":" + denied, "rare restricted", "tenant-b", List.of(0F, 1F, 0F, 0F));
                RedisAclAwareHybridSearchOperations hybrid = new RedisAclAwareHybridSearchOperations(
                        client, embedding(), new HybridStoreOptions(true, "embedding", "content", null, 10,
                        HybridStoreOptions.FallbackMode.CORE_RRF), new RedisSearchAclFilterCompiler(), Map.of("documents", index));

                var results = hybrid.hybridSearch("documents", "ordinary", "rare", 5,
                        HybridSearchOptions.builder().vectorWeight(0.3D).keywordWeight(0.7D).build(),
                        VectorFilter.eq("tenantId", "tenant-a"));
                assertThat(results).extracting(result -> result.getId()).containsExactly(allowed);
                assertThat(results).noneMatch(result -> denied.equals(result.getId()));

                client.jsonDel(index + ":" + allowed);
                assertThat(hybrid.hybridSearch("documents", "ordinary", "ordinary", 5, null,
                        VectorFilter.eq("tenantId", "tenant-a"))).isEmpty();
            } finally {
                client.ftDropIndexDD(index);
            }
        }
    }

    private static void createIndex(RedisClient client, String index) {
        Map<String, Object> vector = Map.of("TYPE", "FLOAT32", "DIM", 4, "DISTANCE_METRIC", "COSINE");
        String response = client.ftCreate(index, FTCreateParams.createParams().on(IndexDataType.JSON).addPrefix(index + ":"), List.of(
                TextField.of("$.content").as("content"), TagField.of("$.tenantId").as("tenantId"),
                VectorField.builder().fieldName("$.embedding").algorithm(VectorField.VectorAlgorithm.FLAT)
                        .attributes(vector).as("embedding").build()));
        assertThat(response).isEqualTo("OK");
    }

    private static void put(RedisClient client, String key, String content, String tenantId, List<Float> embedding) {
        String values = embedding.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        String document = "{\"content\":\"" + content + "\",\"tenantId\":\"" + tenantId
                + "\",\"embedding\":[" + values + "]}";
        assertThat(client.jsonSetWithPlainString(key, Path.ROOT_PATH, document)).isEqualTo("OK");
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
