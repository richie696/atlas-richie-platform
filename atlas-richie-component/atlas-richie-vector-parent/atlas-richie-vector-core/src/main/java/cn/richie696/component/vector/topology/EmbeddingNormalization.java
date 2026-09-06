/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.topology;

/** Declared output normalization contract of an Embedding binding. */
public enum EmbeddingNormalization {
    /** The model or adapter does not expose a normalization guarantee. */
    UNSPECIFIED,
    /** The adapter returns vectors normalized to unit L2 length. */
    UNIT_L2,
    /** The adapter explicitly returns vectors without normalization. */
    NONE
}
