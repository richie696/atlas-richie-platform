/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.filter;

import cn.richie696.component.vector.model.VectorFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QdrantVectorFilterCompilerTest {

    private final QdrantVectorFilterCompiler compiler = new QdrantVectorFilterCompiler();

    @Test
    void compilesAclAndRangeNodesSupportedByTheQdrantConverter() {
        assertThat(compiler.compile(VectorFilter.and(
                VectorFilter.eq("tenantId", "tenant-a"),
                VectorFilter.in("principalId", List.of("user-1", "group-1")),
                VectorFilter.range("updatedAt", 100L, 200L))))
                .isEqualTo("(tenantId == 'tenant-a' AND principalId IN ['user-1', 'group-1'] AND "
                        + "(updatedAt >= 100 AND updatedAt <= 200))");
    }

    @Test
    void rejectsUnsupportedOrAmbiguousValuesBeforeSdkConversion() {
        assertThatThrownBy(() -> compiler.compile(VectorFilter.exists("tenantId")))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiler.compile(VectorFilter.eq("enabled", true)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("string or 64-bit integer");
        assertThatThrownBy(() -> compiler.compile(VectorFilter.eq("score", 0.5D)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> compiler.compile(VectorFilter.in("principalId", List.of("user-1", 2L))))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("homogeneous");
        assertThatThrownBy(() -> compiler.compile(VectorFilter.range("updatedAt", "yesterday", null)))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("finite numeric");
    }

    @Test
    void rejectsFieldInjectionThroughTheSharedSafeDslCompiler() {
        assertThatThrownBy(() -> compiler.compile(VectorFilter.eq("tenantId OR true", "tenant-a")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("illegal filter field");
    }
}
