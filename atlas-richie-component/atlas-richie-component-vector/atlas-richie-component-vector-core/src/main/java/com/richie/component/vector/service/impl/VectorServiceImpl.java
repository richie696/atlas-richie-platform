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
import com.richie.component.vector.model.VectorQuery;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@ConditionalOnMissingBean(VectorService.class)
public abstract class VectorServiceImpl implements VectorService {

    public static final int MAX_LIST_LIMIT = 1000;
    protected static final int LIST_DOCUMENTS_BATCH_SIZE = 200;

    protected final VectorStore vectorStore;
    protected final EmbeddingModel embeddingModel;

    /**
     * 构造 VectorServiceImpl。
     *
     * @param vectorStore    向量存储接口，非空
     * @param embeddingModel 嵌入模型，可为空（当未引入 richie-component-ai 或未配置模型时为空）。
     *                       为空时 search() 方法返回的 VectorSearchResult.vector 为 null
     */
    public VectorServiceImpl(VectorStore vectorStore, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public String addDocument(VectorDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("文档不能为空");
        }
        if (document.getContent() == null || document.getContent().isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (document.getId() == null) {
            document.setId(UUID.randomUUID().toString());
        }
        Document doc = toAiDocument(document);
        vectorStore.add(Collections.singletonList(doc));
        return document.getId();
    }

    @Override
    public List<String> addDocuments(List<VectorDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("文档列表不能为空");
        }
        documents.forEach(doc -> {
            if (doc.getId() == null) doc.setId(UUID.randomUUID().toString());
        });
        List<Document> aiDocs = documents.stream().map(this::toAiDocument).collect(Collectors.toList());
        vectorStore.add(aiDocs);
        return documents.stream().map(VectorDocument::getId).collect(Collectors.toList());
    }

    @Override
    public void updateDocument(String id, VectorDocument document) {
        document.setId(id);
        deleteDocument(id);
        addDocument(document);
    }

    @Override
    public void deleteDocument(String id) {
        vectorStore.delete(id);
    }

    @Override
    public void deleteDocuments(List<String> ids) {
        vectorStore.delete(ids);
    }

    @Override
    public VectorDocument getDocument(String id) {
        SearchRequest request = SearchRequest.builder()
                .filterExpression("id == '%s'".formatted(id))
                .topK(1)
                .build();
        var results = vectorStore.similaritySearch(request);
        if (results != null && !results.isEmpty()) {
            return fromAiDocument(results.getFirst());
        }
        return null;
    }

    @Override
    public List<VectorDocument> getDocuments(List<String> ids) {
        List<VectorDocument> docs = new ArrayList<>();
        for (String id : ids) {
            VectorDocument doc = getDocument(id);
            if (doc != null) docs.add(doc);
        }
        return docs;
    }

    @Override
    public List<VectorSearchResult> search(VectorQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("查询条件不能为空");
        }
        if (query.getText() == null || query.getText().isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (query.getLimit() != null && query.getLimit() <= 0) {
            throw new IllegalArgumentException("limit 必须大于 0");
        }
        if (query.getMinScore() != null && (query.getMinScore() < 0 || query.getMinScore() > 1)) {
            throw new IllegalArgumentException("minScore 必须在 [0, 1] 范围内");
        }

        int topK = query.getLimit() != null ? query.getLimit() : 10;
        double minScore = query.getMinScore() != null ? query.getMinScore() : 0.0;

        SearchRequest request = SearchRequest.builder()
                .query(query.getText())
                .topK(topK)
                .similarityThreshold(minScore)
                .build();

        List<Document> results = vectorStore.similaritySearch(request);
        if (results.isEmpty()) {
            return List.of();
        }
        return results.stream().map(r -> VectorSearchResult.of(
                r.getId(),
                r.getFormattedContent(),
                r.getScore(),
                embeddingModel != null ? embeddingModel.embed(r.getText() == null ? "" : r.getText()) : null
        )).collect(Collectors.toList());
    }

    @Override
    public abstract List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit);

    @Override
    public List<VectorSearchResult> searchByText(String text, int limit) {
        return search(new VectorQuery().setText(text).setLimit(limit));
    }

    @Override
    public List<VectorSearchResult> searchByVector(String indexName, float[] vector, int limit, double minScore) {
        return searchByVector(indexName, vector, limit).stream()
                .filter(r -> r.getScore() != null && r.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    @Override
    public List<VectorSearchResult> searchByText(String text, int limit, double minScore) {
        return searchByText(text, limit).stream()
                .filter(r -> r.getScore() != null && r.getScore() >= minScore)
                .collect(Collectors.toList());
    }

    @Override
    public void createIndex(String indexName, VectorProperties.IndexConfig config) {
        throw new UnsupportedOperationException("创建索引功能未实现");
    }

    @Override
    public void deleteIndex(String indexName) {
        throw new UnsupportedOperationException("删除索引功能未实现");
    }

    @Override
    public boolean indexExists(String indexName) {
        throw new UnsupportedOperationException("检查索引存在性功能未实现");
    }

    @Override
    public VectorProperties.IndexConfig getIndexConfig(String indexName) {
        throw new UnsupportedOperationException("获取索引配置功能未实现");
    }

    @Override
    public long countDocuments(String indexName) {
        throw new UnsupportedOperationException("计数文档功能未实现");
    }

    @Override
    public final List<VectorDocument> listDocuments(String indexName, int offset, int limit) {
        if (limit <= 0) return List.of();
        int cappedLimit = Math.min(limit, MAX_LIST_LIMIT);
        return listDocumentsHandler(indexName, offset, cappedLimit);
    }

    protected abstract List<VectorDocument> listDocumentsHandler(String indexName, int offset, int limit);

    private Document toAiDocument(VectorDocument doc) {
        Document aiDoc = new Document(doc.getId(), doc.getContent(), doc.getMetadata());
        if (doc.getMetadata() != null) aiDoc.getMetadata().putAll(doc.getMetadata());
        if (doc.getType() != null) aiDoc.getMetadata().put("type", doc.getType());
        if (doc.getTags() != null) aiDoc.getMetadata().put("tags", String.join(",", doc.getTags()));
        if (doc.getSource() != null) aiDoc.getMetadata().put("source", doc.getSource());
        if (doc.getStatus() != null) aiDoc.getMetadata().put("status", doc.getStatus());
        if (doc.getNamespace() != null) aiDoc.getMetadata().put("namespace", doc.getNamespace());
        return aiDoc;
    }

    private VectorDocument fromAiDocument(Document aiDoc) {
        VectorDocument doc = new VectorDocument();
        doc.setId(aiDoc.getId());
        doc.setContent(aiDoc.getFormattedContent());
        doc.setMetadata(aiDoc.getMetadata());
        if (aiDoc.getMetadata().containsKey("type")) doc.setType((String) aiDoc.getMetadata().get("type"));
        if (aiDoc.getMetadata().containsKey("tags")) doc.setTags(((String) aiDoc.getMetadata().get("tags")).split(","));
        if (aiDoc.getMetadata().containsKey("source")) doc.setSource((String) aiDoc.getMetadata().get("source"));
        if (aiDoc.getMetadata().containsKey("status")) doc.setStatus((String) aiDoc.getMetadata().get("status"));
        if (aiDoc.getMetadata().containsKey("namespace"))
            doc.setNamespace((String) aiDoc.getMetadata().get("namespace"));
        return doc;
    }
}
