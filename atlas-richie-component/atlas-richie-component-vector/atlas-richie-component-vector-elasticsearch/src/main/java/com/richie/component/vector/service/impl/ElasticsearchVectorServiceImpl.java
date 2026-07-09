/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.elasticsearch.ElasticsearchVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

import org.apache.commons.lang3.ArrayUtils;

@Service
@ConditionalOnProperty(prefix = "platform.component.vector", name = "provider", havingValue = "elasticsearch")
public class ElasticsearchVectorServiceImpl extends VectorServiceImpl implements VectorService {

    private static final String VECTOR_FIELD = "vector";

    @Autowired
    public ElasticsearchVectorServiceImpl(VectorStore vectorStore,
                                          @Qualifier("aiEmbeddingModel") EmbeddingModel embeddingModel) {
        super(vectorStore, embeddingModel);
    }

    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            IndexSettings settings = new IndexSettings.Builder()
                    .numberOfShards(config.getShards().toString())
                    .numberOfReplicas(config.getReplicas().toString())
                    .build();

            TypeMapping mappings = new TypeMapping.Builder()
                    .properties("content", p -> p.text(t -> t))
                    .properties(VECTOR_FIELD, p -> p.denseVector(dv -> dv.dims(config.getDimension())))
                    .build();

            CreateIndexRequest req = new CreateIndexRequest.Builder()
                    .index(indexName)
                    .settings(settings)
                    .mappings(mappings)
                    .build();

            CreateIndexResponse resp = client.indices().create(req);
            if (!resp.acknowledged()) {
                throw new RuntimeException("创建索引未被acknowledged");
            }
        } catch (Exception e) {
            throw new RuntimeException("创建索引失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public void deleteIndex(String indexName) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            DeleteIndexRequest req = new DeleteIndexRequest.Builder().index(indexName).build();
            DeleteIndexResponse resp = client.indices().delete(req);
            if (!resp.acknowledged()) {
                throw new RuntimeException("删除索引未被acknowledged");
            }
        } catch (Exception e) {
            throw new RuntimeException("删除索引失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public boolean indexExists(String indexName) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            GetIndexRequest req = new GetIndexRequest.Builder().index(indexName).build();
            GetIndexResponse resp = client.indices().get(req);
            return resp.get(indexName) != null;
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                return false;
            }
            throw new RuntimeException("检查索引存在性失败: %s".formatted(e.getMessage()), e);
        } catch (Exception e) {
            throw new RuntimeException("检查索引存在性失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            GetIndexResponse response = client.indices().get(g -> g.index(indexName));
            IndexState indexState = response.get(indexName);
            IndexSettings settings = Objects.requireNonNull(indexState).settings();
            TypeMapping mapping = indexState.mappings();
            return getIndexConfig(indexName, settings, mapping);
        } catch (Exception e) {
            throw new RuntimeException("获取索引配置失败: %s".formatted(e.getMessage()), e);
        }
    }

    private static VectorProperties.IndexConfig getIndexConfig(String indexName, IndexSettings settings, TypeMapping mapping) {
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig();
        config.setName(indexName);

        if (settings != null) {
            if (settings.numberOfShards() != null) {
                config.setShards(Integer.parseInt(settings.numberOfShards()));
            }
            if (settings.numberOfReplicas() != null) {
                config.setReplicas(Integer.parseInt(settings.numberOfReplicas()));
            }
        }

        if (mapping != null && mapping.properties() != null) {
            Map<String, Object> additionalFields = new HashMap<>(mapping.properties());
            config.setAdditionalFields(additionalFields);
        }

        return config;
    }

    @Override
    public long countDocuments(String indexName) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            CountRequest req = new CountRequest.Builder().index(indexName).build();
            CountResponse resp = client.count(req);
            return resp.count();
        } catch (Exception e) {
            throw new RuntimeException("统计文档数失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    protected List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit) {
        ElasticsearchClient client = getElasticsearchClient();
        try {
            @SuppressWarnings("unchecked")
            SearchResponse<Map<String, Object>> resp = client.search(s -> s
                            .index(indexName)
                            .from(offset)
                            .size(limit)
                            .query(q -> q.matchAll(m -> m))
                            .source(src -> src.filter(f -> f.includes("content", "metadata").excludes(VECTOR_FIELD)))
                            .trackTotalHits(t -> t.count(10000))
                    , (Class<Map<String, Object>>) (Class<?>) Map.class);

            List<VectorDocument> docs = new ArrayList<>();
            for (Hit<Map<String, Object>> hit : resp.hits().hits()) {
                Map<String, Object> src = hit.source();
                if (src == null) continue;

                VectorDocument doc = new VectorDocument();
                doc.setId(hit.id());
                doc.setContent((String) src.getOrDefault("content", ""));
                //noinspection unchecked
                doc.setMetadata((Map<String, Object>) src.getOrDefault("metadata", Map.of()));
                docs.add(doc);
            }
            return docs;
        } catch (Exception e) {
            throw new RuntimeException("分页查询文档失败: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] queryVector, int limit) {
        if (queryVector == null || queryVector.length == 0) {
            throw new IllegalArgumentException("查询向量不能为空");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }

        ElasticsearchClient client = getElasticsearchClient();

        try {
            // 空向量用 List.of()，非空用 Arrays.asList() 避免额外数组分配
            List<Float> vector = Arrays.asList(ArrayUtils.toObject(queryVector));

            @SuppressWarnings("unchecked")
            SearchResponse<Map<String, Object>> response = (SearchResponse<Map<String, Object>>) client.search(s -> s
                            .index(indexName)
                            .knn(knn -> knn
                                    .field(VECTOR_FIELD)
                                    .queryVector(vector)
                                    .k(limit)
                            )
                            .source(src -> src.filter(f -> f.excludes(VECTOR_FIELD)))
                    , (Class<Map<String, Object>>) (Class<?>) Map.class);

            List<VectorSearchResult> results = new ArrayList<>();
            for (Hit<Map<String, Object>> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                String id = hit.id();
                String content = (String) source.get("content");
                Double score = hit.score();

                results.add(VectorSearchResult.of(id, content, score, new float[0]));
            }

            return results;
        } catch (Exception e) {
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    protected ElasticsearchClient getElasticsearchClient() {
        if (vectorStore instanceof ElasticsearchVectorStore esStore) {
            return (ElasticsearchClient) esStore.getNativeClient().orElseThrow();
        }
        throw new UnsupportedOperationException("VectorStore 不是 ElasticsearchVectorStore 实例");
    }

}