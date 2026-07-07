package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PathDimensionTest {

    private final PathDimension dim = new PathDimension();

    @Test
    void name_isPath() {
        assertThat(dim.name()).isEqualTo("path");
    }

    @Test
    void returnsPath() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .path("/api/v1/orders").build();
        assertThat(dim.extract(ctx)).isEqualTo("/api/v1/orders");
    }

    @Test
    void emptyPath_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .path("").build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void blankPath_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .path("   ").build();
        assertThat(dim.extract(ctx)).isNull();
    }
}