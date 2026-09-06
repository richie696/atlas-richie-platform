/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Provider-neutral retrieval score stages. */
public enum VectorScoreStage {
    RAW_DISTANCE,
    VECTOR_SCORE,
    LEXICAL_SCORE,
    FUSED_SCORE,
    RERANK_SCORE,
    FINAL_SCORE
}
