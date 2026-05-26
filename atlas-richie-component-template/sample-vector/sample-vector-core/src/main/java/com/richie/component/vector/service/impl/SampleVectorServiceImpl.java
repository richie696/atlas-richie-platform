package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.model.VectorQuery;
import com.richie.component.vector.model.VectorSearchResult;
import com.richie.component.vector.service.SampleVectorService;
import com.richie.component.vector.service.VectorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SampleVectorServiceImpl implements SampleVectorService {

    private final VectorService vectorService;

    @Override
    public String upsertText(String id,
                             String content,
                             String type,
                             List<String> tags,
                             String namespace,
                             Map<String, Object> metadata) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("content 不能为空");
        }
        VectorDocument doc = new VectorDocument()
                .setId(StringUtils.hasText(id) ? id : null)
                .setContent(content)
                .setType(type)
                .setTags(tags == null ? null : tags.toArray(new String[0]))
                .setNamespace(namespace)
                .setMetadata(metadata == null ? new HashMap<>() : new HashMap<>(metadata))
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setStatus("active");

        if (doc.getId() == null) {
            return vectorService.addDocument(doc);
        }
        vectorService.updateDocument(doc.getId(), doc);
        return doc.getId();
    }

    @Override
    public List<String> upsertBatch(List<UpsertItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        List<VectorDocument> docs = new ArrayList<>(items.size());
        for (UpsertItem item : items) {
            if (!StringUtils.hasText(item.content)) {
                continue;
            }
            VectorDocument doc = new VectorDocument()
                    .setId(StringUtils.hasText(item.id) ? item.id : null)
                    .setContent(item.content)
                    .setType(item.type)
                    .setTags(item.tags == null ? null : item.tags.toArray(new String[0]))
                    .setNamespace(item.namespace)
                    .setMetadata(item.metadata == null ? new HashMap<>() : new HashMap<>(item.metadata))
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now())
                    .setStatus("active");
            docs.add(doc);
        }
        return vectorService.addDocuments(docs);
    }

    @Override
    public List<VectorSearchResult> searchByText(String text,
                                                 Integer limit,
                                                 Double minScore,
                                                 String type,
                                                 List<String> tags,
                                                 String namespace) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("text 不能为空");
        }
        VectorQuery query = new VectorQuery()
                .setText(text)
                .setLimit(limit == null ? 10 : limit)
                .setMinScore(minScore)
                .setType(type)
                .setTags(tags)
                .setNamespace(namespace)
                .setIncludeVector(false)
                .setIncludeMetadata(true)
                .setSortBy("score");
        List<VectorSearchResult> results = vectorService.search(query);
        if (minScore != null) {
            results = results.stream()
                    .filter(r -> r.getScore() != null && r.getScore() >= minScore)
                    .toList();
        }
        return results;
    }

    @Override
    public List<VectorSearchResult> searchByVector(float[] vector,
                                                   Integer limit,
                                                   Double minScore,
                                                   String type,
                                                   List<String> tags,
                                                   String namespace) {
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("vector 不能为空");
        }
        VectorQuery query = new VectorQuery()
                .setVector(vector)
                .setLimit(limit == null ? 10 : limit)
                .setMinScore(minScore)
                .setType(type)
                .setTags(tags)
                .setNamespace(namespace)
                .setIncludeVector(false)
                .setIncludeMetadata(true)
                .setSortBy("score");
        List<VectorSearchResult> results = vectorService.search(query);
        if (minScore != null) {
            results = results.stream()
                    .filter(r -> r.getScore() != null && r.getScore() >= minScore)
                    .toList();
        }
        return results;
    }

    @Override
    public void createIndex(String indexName, Integer dimension, String metric) {
        if (!StringUtils.hasText(indexName)) {
            throw new IllegalArgumentException("indexName 不能为空");
        }
        VectorProperties.IndexConfig cfg = new VectorProperties.IndexConfig()
                .setName(indexName)
                .setDimension(dimension == null ? 768 : dimension)
                .setMetric(StringUtils.hasText(metric) ? metric : "cosine");
        vectorService.createIndex(indexName, cfg);
    }

    @Override
    public boolean indexExists(String indexName) {
        return vectorService.indexExists(indexName);
    }

    @Override
    public void deleteIndex(String indexName) {
        vectorService.deleteIndex(indexName);
    }

    @Override
    public long count(String indexName) {
        return vectorService.countDocuments(indexName);
    }
}


