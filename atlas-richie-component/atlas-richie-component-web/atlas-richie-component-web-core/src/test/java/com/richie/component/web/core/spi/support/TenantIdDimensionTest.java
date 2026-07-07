package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TenantIdDimensionTest {

    private final TenantIdDimension dim = new TenantIdDimension();

    @Test
    void name_isTenant() {
        assertThat(dim.name()).isEqualTo("tenant");
    }

    @Test
    void headerPresent_returnsTrimmedValue() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Tenant-Id", "  acme  ").build();
        assertThat(dim.extract(ctx)).isEqualTo("acme");
    }

    @Test
    void headerMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void headerBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Tenant-Id", "   ").build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void defaultHeaderIsXTenantId() {
        assertThat(TenantIdDimension.DEFAULT_HEADER).isEqualTo("X-Tenant-Id");
    }
}