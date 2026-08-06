package cn.richie696.component.chunking.model;

/**
 * 切片算法可观测的非致命事实，可用于质量指标与审计。
 */
public enum ChunkingSignal {
    HARD_TRUNCATED,
    SEPARATOR_NOT_FOUND,
    TOKEN_LIMIT_FORCED,
    SEMANTIC_FALLBACK,
    INVALID_SEMANTIC_BOUNDARY
}
