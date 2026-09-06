/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query.postgresql;

import cn.richie696.component.vector.query.ProviderQueryOptions;
import cn.richie696.component.vector.query.VectorQueryErrorCode;
import cn.richie696.component.vector.query.VectorQueryValidationException;

/** Typed pgvector transaction-local query tuning. */
public record PostgresqlQueryOptions(Integer hnswEfSearch, Integer ivfflatProbes)
        implements ProviderQueryOptions {

    public static final int MAX_VALUE = 32_768;

    public PostgresqlQueryOptions {
        validate("hnswEfSearch", hnswEfSearch);
        validate("ivfflatProbes", ivfflatProbes);
        if (hnswEfSearch != null && ivfflatProbes != null) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.CONFLICTING_OPTIONS,
                    "PostgreSQL hnswEfSearch and ivfflatProbes are mutually exclusive");
        }
    }

    @Override
    public String extensionId() {
        return "postgresql";
    }

    @Override
    public String version() {
        return "1.0";
    }

    private static void validate(String name, Integer value) {
        if (value != null && (value < 1 || value > MAX_VALUE)) {
            throw new VectorQueryValidationException(
                    VectorQueryErrorCode.INVALID_VALUE,
                    "PostgreSQL " + name + " is outside the supported range");
        }
    }
}
