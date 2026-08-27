/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.observation;

/**
 * 可观测的检索阶段。
 *
 * <p>阶段只描述执行边界，不携带查询正文、ACL 条件或 provider 私有请求，
 * 便于上层安全地记录检索质量指标。</p>
 */
public enum RetrievalStage {
    /** 查询文本 embedding。 */
    EMBEDDING,
    /** 向量库候选召回；某些 provider 会把 embedding 合并在该阶段内。 */
    VECTOR_SEARCH,
    /** 候选结果的模型重排序。 */
    RERANK,
    /** MMR 与单文档多样性截断。 */
    DIVERSIFY,
    /** 知识库检索端到端耗时。 */
    TOTAL
}
