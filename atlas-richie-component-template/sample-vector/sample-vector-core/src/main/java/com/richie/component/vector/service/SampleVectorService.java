package com.richie.component.vector.service;

import com.richie.component.vector.model.VectorSearchResult;

import java.util.List;
import java.util.Map;

/**
 * 示例业务服务（高层 API）：
 * - 入参尽量精简，内部构建 VectorDocument/VectorQuery 并处理校验与默认值
 */
public interface SampleVectorService {

    String upsertText(String id,
                      String content,
                      String type,
                      List<String> tags,
                      String namespace,
                      Map<String, Object> metadata);

    List<String> upsertBatch(List<UpsertItem> items);

    List<VectorSearchResult> searchByText(String text,
                                          Integer limit,
                                          Double minScore,
                                          String type,
                                          List<String> tags,
                                          String namespace);

    List<VectorSearchResult> searchByVector(float[] vector,
                                            Integer limit,
                                            Double minScore,
                                            String type,
                                            List<String> tags,
                                            String namespace);

    void createIndex(String indexName, Integer dimension, String metric);

    boolean indexExists(String indexName);

    void deleteIndex(String indexName);

    long count(String indexName);

    class UpsertItem {
        public String id;
        public String content;
        public String type;
        public List<String> tags;
        public String namespace;
        public Map<String, Object> metadata;
    }
}


