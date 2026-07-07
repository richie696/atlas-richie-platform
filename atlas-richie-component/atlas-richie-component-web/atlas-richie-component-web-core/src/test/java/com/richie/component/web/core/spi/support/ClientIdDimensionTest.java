package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.config.ratelimit.WebFilterProperties;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIdDimensionTest {

    private static ClientIdDimension dim(String header) {
        WebFilterProperties props = new WebFilterProperties();
        props.setKeyHeader(header);
        return new ClientIdDimension(props);
    }

    @Test
    void name_isClient() {
        assertThat(dim("X-Client-Id").name()).isEqualTo("client");
    }

    @Test
    void headerPresent_returnsTrimmedValue() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Client-Id", "  user-42  ").build();
        assertThat(dim("X-Client-Id").extract(ctx)).isEqualTo("user-42");
    }

    @Test
    void headerMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim("X-Client-Id").extract(ctx)).isNull();
    }

    @Test
    void headerBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Client-Id", "   ").build();
        assertThat(dim("X-Client-Id").extract(ctx)).isNull();
    }

    @Test
    void customHeaderName_isRespected() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-User-Id", "alice").build();
        assertThat(dim("X-User-Id").extract(ctx)).isEqualTo("alice");
    }
}