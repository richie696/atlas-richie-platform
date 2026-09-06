/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.query.vikingdb;

import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchAdvanceOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbSearchCommonOptions;
import cn.richie696.ai.vectorstore.vikingdb.model.VikingDbVectorSearchRequest;
import cn.richie696.component.vector.query.ProviderQueryOptions;

import java.util.Map;

/**
 * Typed per-query VikingDB controls for the provider-neutral advanced text API.
 *
 * <p>Dense/sparse/hybrid requests, BM25 and provider rerank remain available from
 * the explicit VikingDB native capability exposed by the Store handle. This
 * extension is intentionally limited to the generic text-query entry point and
 * carries only the stable AI-store option models.</p>
 */
public record VikingDbQueryOptions(
        VikingDbVectorSearchRequest.Mode mode,
        VikingDbSearchCommonOptions common,
        VikingDbSearchAdvanceOptions advance,
        Map<String, Float> sparseVector) implements ProviderQueryOptions {

    public VikingDbQueryOptions {
        mode = mode == null ? VikingDbVectorSearchRequest.Mode.DENSE : mode;
        common = common == null ? VikingDbSearchCommonOptions.empty() : common;
        advance = advance == null ? VikingDbSearchAdvanceOptions.empty() : advance;
        sparseVector = sparseVector == null ? Map.of() : Map.copyOf(sparseVector);
    }

    public VikingDbQueryOptions(VikingDbSearchCommonOptions common,
                                VikingDbSearchAdvanceOptions advance) {
        this(VikingDbVectorSearchRequest.Mode.DENSE, common, advance, Map.of());
    }

    public static VikingDbQueryOptions empty() {
        return new VikingDbQueryOptions(
                VikingDbVectorSearchRequest.Mode.DENSE,
                VikingDbSearchCommonOptions.empty(), VikingDbSearchAdvanceOptions.empty(), Map.of());
    }

    @Override
    public String extensionId() {
        return "vikingdb";
    }

    @Override
    public String version() {
        return "1.0";
    }
}
