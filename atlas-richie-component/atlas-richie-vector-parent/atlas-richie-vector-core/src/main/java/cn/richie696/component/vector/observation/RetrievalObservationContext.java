/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

import java.util.UUID;

/**
 * 单次检索的非敏感关联上下文。
 *
 * @param operationId       单次检索关联 ID；用于跨阶段聚合，不是业务主键
 * @param indexName         逻辑索引名
 * @param requestedLimit    调用方请求的候选/结果上限
 * @param hybrid            是否走 hybrid 路径
 * @param rerankRequested   是否请求 rerank
 * @param diversifyRequested 是否请求 MMR/多样性处理
 */
public record RetrievalObservationContext(String operationId, String indexName, int requestedLimit,
                                          boolean hybrid, boolean rerankRequested,
                                          boolean diversifyRequested) {

    public RetrievalObservationContext {
        operationId = operationId == null || operationId.isBlank() ? UUID.randomUUID().toString() : operationId;
        indexName = indexName == null ? "" : indexName;
        if (requestedLimit < 0) {
            throw new IllegalArgumentException("requestedLimit must not be negative");
        }
    }

    public static RetrievalObservationContext forTextSearch(String indexName, int requestedLimit,
                                                             boolean rerankRequested) {
        return new RetrievalObservationContext(UUID.randomUUID().toString(), indexName, requestedLimit,
                false, rerankRequested, false);
    }

    public static RetrievalObservationContext forKnowledgeSearch(String indexName, int requestedLimit,
                                                                  boolean hybrid, boolean rerankRequested,
                                                                  boolean diversifyRequested) {
        return new RetrievalObservationContext(UUID.randomUUID().toString(), indexName, requestedLimit,
                hybrid, rerankRequested, diversifyRequested);
    }
}
