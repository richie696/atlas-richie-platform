/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import io.weaviate.client.v1.misc.model.ReplicationConfig;
import io.weaviate.client.v1.misc.model.VectorIndexConfig;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "weaviate")
public class WeaviateVectorServiceImpl extends VectorServiceImpl implements VectorService {

    private final WeaviateClient weaviateClient;

    @Autowired
    public WeaviateVectorServiceImpl(VectorStore vectorStore,
                                     @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel,
                                     WeaviateClient weaviateClient) {
        super(vectorStore, embeddingModel);
        this.weaviateClient = weaviateClient;
    }

    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        VectorIndexConfig vectorConfig = VectorIndexConfig.builder()
                .distance(config.getMetric() != null ? config.getMetric() : "cosine")
                .efConstruction(128)
                .maxConnections(64)
                .build();

        ReplicationConfig replicationConfig = ReplicationConfig.builder()
                .factor(config.getReplicas() != null ? config.getReplicas() : 1)
                .build();

        WeaviateClass weaviateClass = WeaviateClass.builder()
                .className(indexName)
                .vectorIndexType(config.getIndexType() != null ? config.getIndexType() : "hnsw")
                .vectorizer("none")
                .vectorIndexConfig(vectorConfig)
                .replicationConfig(replicationConfig)
                .build();

        Result<Boolean> result = weaviateClient.schema().classCreator().withClass(weaviateClass).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate createIndex failed: " + result.getError().getMessages());
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        Result<Boolean> result = weaviateClient.schema().exists().withClassName(indexName).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate indexExists failed: " + result.getError().getMessages());
        }
        return result.getResult() != null && result.getResult();
    }

    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        Result<WeaviateClass> result = weaviateClient.schema().classGetter().withClassName(indexName).run();
        if (result.hasErrors() || result.getResult() == null) {
            return null;
        }
        WeaviateClass clazz = result.getResult();
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(clazz.getClassName());
        config.setIndexType(clazz.getVectorIndexType());
        if (clazz.getVectorIndexConfig() != null) {
            config.setMetric(clazz.getVectorIndexConfig().getDistance());
        }
        if (clazz.getReplicationConfig() != null) {
            config.setReplicas(clazz.getReplicationConfig().getFactor());
        }
        return config;
    }

    @Override
    public long countDocuments(String indexName) {
        String graphql = "{ Get { " + indexName + " { _additional { id } } } }";
        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate countDocuments failed: " + result.getError().getMessages());
        }
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return 0;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return 0;
        }
        List<?> items = (List<?>) getResult.get(indexName);
        return items != null ? items.size() : 0;
    }

    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        String graphql = String.format("""
                {
                  Get {
                    %s(offset: %d, limit: %d) {
                      content
                      _additional {
                        id
                      }
                    }
                  }
                }
                """, indexName, offset, limit);

        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate listDocumentsHandler failed: " + result.getError().getMessages());
        }

        List<VectorDocument> docs = new ArrayList<>();
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return docs;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return docs;
        }
        var items = (List<?>) getResult.get(indexName);
        if (items == null) {
            return docs;
        }

        for (Object item : items) {
            Map<String, ?> itemMap = (Map<String, ?>) item;
            Map<String, ?> additional = (Map<String, ?>) itemMap.get("_additional");
            if (additional == null) {
                continue;
            }
            String id = (String) additional.get("id");
            String content = (String) itemMap.get("content");

            VectorDocument doc = new VectorDocument();
            doc.setId(id);
            doc.setContent(content);
            doc.setVector(new float[0]);
            docs.add(doc);
        }
        return docs;
    }

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        String vectorStr = vectorToString(queryVector);

        String graphql = String.format("""
                {
                  Get {
                    %s(
                      nearVector: {
                        vector: %s
                      }
                      limit: %d
                    ) {
                      content
                      _additional {
                        id
                        certainty
                      }
                    }
                  }
                }
                """, indexName, vectorStr, limit);

        var result = weaviateClient.graphQL().raw().withQuery(graphql).run();
        if (result.hasErrors()) {
            throw new RuntimeException("Weaviate searchByVector failed: " + result.getError().getMessages());
        }

        List<VectorSearchResult> results = new ArrayList<>();
        Map<?, ?> data = (Map<?, ?>) result.getResult().getData();
        if (data == null) {
            return results;
        }
        Map<?, ?> getResult = (Map<?, ?>) data.get("Get");
        if (getResult == null) {
            return results;
        }
        var items = (List<?>) getResult.get(indexName);
        if (items == null) {
            return results;
        }

        for (Object item : items) {
            Map<String, ?> itemMap = (Map<String, ?>) item;
            Map<String, ?> additional = (Map<String, ?>) itemMap.get("_additional");
            if (additional == null) {
                continue;
            }

            String id = (String) additional.get("id");
            String content = (String) itemMap.get("content");
            double certainty = 0.0;
            if (additional.get("certainty") != null) {
                certainty = ((Number) additional.get("certainty")).doubleValue();
            }

            if (id != null) {
                results.add(VectorSearchResult.of(id, content, certainty, new float[0]));
            }
        }
        return results;
    }

    private String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }

}
