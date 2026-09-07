/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbCollectionOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbDocumentOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbIndexOperations;
import cn.richie696.ai.vectorstore.vikingdb.api.VikingDbSearchOperations;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbDocumentRecord;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbResourceRef;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchCommonOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchResponse;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbVectorSearchRequest;
import cn.richie696.component.vector.enums.VectorProvider;
import cn.richie696.component.vector.filter.VikingDbVectorFilterAdapter;
import cn.richie696.component.vector.model.VectorFilter;
import cn.richie696.component.vector.topology.VectorConnectionDefinition;
import cn.richie696.component.vector.topology.VectorConnectionHandle;
import cn.richie696.component.vector.topology.VectorConnectionId;
import cn.richie696.component.vector.topology.VectorEmbeddingModelBinding;
import cn.richie696.component.vector.topology.VectorIndexDefinition;
import cn.richie696.component.vector.topology.VectorStoreDefinition;
import cn.richie696.component.vector.topology.VectorStoreHandle;
import cn.richie696.component.vector.topology.VectorStoreId;
import com.volcengine.ApiException;
import com.volcengine.ApiClient;
import com.volcengine.sign.Credentials;
import com.volcengine.vikingdb.VikingdbApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.embedding.EmbeddingModel;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real VikingDB data-plane acceptance test. It is deliberately opt-in because it creates and
 * deletes a unique cloud collection. Credentials are accepted only through process environment.
 */
@EnabledIfEnvironmentVariable(named = "VECTOR_VIKINGDB_IT_RUN", matches = "true")
class VikingDbNativeLiveIT {

    private static final String DEFAULT_HOST = "api-vikingdb.vikingdb.cn-beijing.volces.com";
    private static final String DEFAULT_CONTROL_ENDPOINT = "vikingdb.cn-beijing.volcengineapi.com";
    private static final String DEFAULT_REGION = "cn-beijing";

    @Test
    void createsIndexesWritesAndAclFiltersAUniqueCollection() {
        String accessKey = requiredEnvironment("VIKINGDB_ACCESS_KEY");
        String secretKey = requiredEnvironment("VIKINGDB_SECRET_KEY");
        String region = optionalEnvironment("VIKINGDB_REGION", DEFAULT_REGION);
        String host = optionalEnvironment("VIKINGDB_HOST", DEFAULT_HOST);
        String controlEndpoint = optionalEnvironment("VIKINGDB_CONTROL_ENDPOINT", DEFAULT_CONTROL_ENDPOINT);
        String projectName = optionalEnvironment("VIKINGDB_PROJECT_NAME", null);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        String collection = "atlasit" + suffix;
        String index = collection + "idx";
        VikingDbResourceRef resource = new VikingDbResourceRef(projectName, collection, index);
        VikingdbApi cleanupControlPlane = controlPlane(controlEndpoint, region, accessKey, secretKey);
        Throwable failure = null;
        boolean controlPlaneVerified = false;

        try {
            assertControlPlaneCanReadMissingResource(cleanupControlPlane, resource);
            controlPlaneVerified = true;
            try (VectorConnectionHandle connection = new VikingDbVectorProviderFactory(null).openConnection(
                connection(accessKey, secretKey, host, controlEndpoint, region))) {
                VikingDbVectorProviderFactory factory = new VikingDbVectorProviderFactory(null);
                VectorStoreHandle handle = factory.createStore(connection,
                        store(collection, index, projectName), VectorEmbeddingModelBinding.of("live-test", embeddingModel()));

                VikingDbCollectionOperations collections = handle.requireCapability(VikingDbCollectionOperations.class);
                VikingDbIndexOperations indexes = handle.requireCapability(VikingDbIndexOperations.class);
                VikingDbDocumentOperations documents = handle.requireCapability(VikingDbDocumentOperations.class);
                VikingDbSearchOperations search = handle.requireCapability(VikingDbSearchOperations.class);
                assertThat(collections.getCollection(resource).fields())
                        .containsKeys("id", "content", "vector", "tenantId", "principalId");
                assertThat(indexes.getIndex(resource).target()).isEqualTo(resource);

                String allowedId = "allowed-" + suffix;
                String deniedId = "denied-" + suffix;
                documents.upsertRecords(List.of(
                        record(allowedId, "tenant-a", "user-1"),
                        record(deniedId, "tenant-b", "user-2")));

                VikingDbSearchResponse response = awaitAuthorizedResult(search, allowedId, deniedId);
                assertThat(response.hits()).extracting(hit -> String.valueOf(hit.id())).containsExactly(allowedId);
                assertThat(response.execution().effectiveFilterDigest()).isNotBlank();
            }
        } catch (RuntimeException | Error throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            if (controlPlaneVerified) {
                try {
                    if (collectionExists(cleanupControlPlane, resource)) {
                        deleteCollectionIfPresent(cleanupControlPlane, collection, projectName);
                    }
                } catch (Exception cleanupFailure) {
                    if (failure != null) {
                        failure.addSuppressed(cleanupFailure);
                    } else {
                        throw new AssertionError("VikingDB live-test cleanup failed", cleanupFailure);
                    }
                }
            }
        }
    }

    private static void assertControlPlaneCanReadMissingResource(
            VikingdbApi controlPlane, VikingDbResourceRef resource) {
        try {
            controlPlane.getVikingdbCollection(new com.volcengine.vikingdb.model.GetVikingdbCollectionRequest()
                    .collectionName(resource.collectionName()).projectName(resource.projectName()));
            throw new AssertionError("VikingDB live-test resource unexpectedly already exists");
        } catch (ApiException exception) {
            if (!isNotFound(exception)) {
                throw new IllegalStateException(
                        "VikingDB control plane is unavailable for E2E resource lifecycle; check region, service activation and IAM",
                        exception);
            }
        }
    }

    private static void deleteCollectionIfPresent(VikingdbApi controlPlane, String collection, String projectName)
            throws ApiException {
        try {
            controlPlane.deleteVikingdbCollection(new com.volcengine.vikingdb.model.DeleteVikingdbCollectionRequest()
                    .collectionName(collection).projectName(projectName));
        } catch (ApiException exception) {
            if (!isNotFound(exception)) throw exception;
        }
    }

    private static boolean collectionExists(VikingdbApi controlPlane, VikingDbResourceRef resource)
            throws ApiException {
        try {
            controlPlane.getVikingdbCollection(new com.volcengine.vikingdb.model.GetVikingdbCollectionRequest()
                    .collectionName(resource.collectionName()).projectName(resource.projectName()));
            return true;
        } catch (ApiException exception) {
            if (isNotFound(exception)) return false;
            throw exception;
        }
    }

    private static boolean isNotFound(ApiException exception) {
        if (exception.getCode() == 404) return true;
        String message = exception.getMessage();
        return message != null && (message.contains("NotFound") || message.contains("not found"));
    }

    private static VikingDbSearchResponse awaitAuthorizedResult(
            VikingDbSearchOperations search, String allowedId, String deniedId) {
        AssertionError lastFailure = null;
        for (int attempt = 1; attempt <= 12; attempt++) {
            try {
                VikingDbSearchResponse response = search.search(VikingDbVectorSearchRequest.builder()
                        .mode(VikingDbVectorSearchRequest.Mode.DENSE)
                        .denseVector(new float[]{1.0F, 0.0F, 0.0F, 0.0F})
                        .queryFilter(VikingDbVectorFilterAdapter.toSpring(VectorFilter.and(
                                VectorFilter.eq("tenantId", "tenant-a"),
                                VectorFilter.in("principalId", List.of("user-1", "group-1")))))
                        .common(VikingDbSearchCommonOptions.builder()
                                .limit(10).outputFields(List.of("content", "tenantId", "principalId")).build())
                        .build());
                List<String> ids = response.hits().stream().map(hit -> String.valueOf(hit.id())).toList();
                if (ids.contains(allowedId) && !ids.contains(deniedId)) {
                    return response;
                }
                lastFailure = new AssertionError("VikingDB index is not ready for ACL filtering yet");
            } catch (RuntimeException exception) {
                lastFailure = new AssertionError("VikingDB search is not ready yet", exception);
            }
            try {
                Thread.sleep(5_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for VikingDB index", exception);
            }
        }
        throw lastFailure == null ? new AssertionError("VikingDB search did not return an authorized document") : lastFailure;
    }

    private static VikingDbDocumentRecord record(String id, String tenantId, String principalId) {
        return new VikingDbDocumentRecord(id, "shared test document", List.of(1.0F, 0.0F, 0.0F, 0.0F),
                Map.of("tenantId", tenantId, "principalId", principalId));
    }

    private static VectorConnectionDefinition connection(
            String accessKey, String secretKey, String host, String controlEndpoint, String region) {
        return new VectorConnectionDefinition(VectorConnectionId.of("viking-live"), VectorProvider.VIKINGDB,
                Map.of("access-key", accessKey, "secret-key", secretKey, "host", host,
                        "control-endpoint", controlEndpoint, "region", region, "scheme", "HTTPS"));
    }

    private static VectorStoreDefinition store(String collection, String index, String projectName) {
        Map<String, Object> additional = new LinkedHashMap<>();
        additional.put("initialize-schema", true);
        additional.put("index-name", index);
        additional.put("metadata-fields", "tenantId:string,principalId:string");
        additional.put("scalar-index", "tenantId,principalId");
        if (projectName != null) additional.put("project-name", projectName);
        VectorIndexDefinition definition = new VectorIndexDefinition(
                "documents", collection, 4, "cosine", "hnsw", 1, 1, additional, Map.of());
        return new VectorStoreDefinition(VectorStoreId.of("viking-live"), VectorConnectionId.of("viking-live"),
                "live-test", "documents", true, Set.of(), Map.of("documents", definition));
    }

    private static VikingdbApi controlPlane(String endpoint, String region, String accessKey, String secretKey) {
        return new VikingdbApi(new ApiClient().setEndpoint(endpoint)
                .setCredentials(Credentials.getCredentials(accessKey, secretKey)).setRegion(region));
    }

    private static EmbeddingModel embeddingModel() {
        return EmbeddingModel.class.cast(Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(), new Class<?>[]{EmbeddingModel.class},
                (proxy, method, arguments) -> {
                    if ("dimensions".equals(method.getName())) return 4;
                    if ("toString".equals(method.getName())) return "VikingDbLiveTestEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == arguments[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 0;
                    return null;
                }));
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " must be configured");
        return value;
    }

    private static String optionalEnvironment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
