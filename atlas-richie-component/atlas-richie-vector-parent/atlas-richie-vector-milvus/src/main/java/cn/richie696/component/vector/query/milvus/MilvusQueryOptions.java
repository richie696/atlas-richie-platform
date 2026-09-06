/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query.milvus;

import cn.richie696.component.vector.query.ProviderQueryOptions;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryValidationException;

/** Typed Milvus per-query tuning. Applicability depends on the bound index type. */
public record MilvusQueryOptions(Integer ef, Integer nprobe) implements ProviderQueryOptions {

    public static final int MAX_VALUE = 32_768;

    public MilvusQueryOptions {
        validate("ef", ef);
        validate("nprobe", nprobe);
        if (ef != null && nprobe != null) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.CONFLICTING_OPTIONS, "Milvus ef and nprobe are mutually exclusive");
        }
    }

    @Override
    public String extensionId() {
        return "milvus";
    }

    @Override
    public String version() {
        return "1.0";
    }

    private static void validate(String name, Integer value) {
        if (value != null && (value < 1 || value > MAX_VALUE)) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE, "Milvus " + name + " is outside the supported range");
        }
    }
}
