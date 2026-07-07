package com.richie.component.web.core.spi.support;

import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpDimensionTest {

    private final IpDimension dim = new IpDimension();

    @Test
    void name_isIp() {
        assertThat(dim.name()).isEqualTo("ip");
    }

    @Test
    void xffPresent_singleIp_returnsIt() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "1.2.3.4").build();
        assertThat(dim.extract(ctx)).isEqualTo("1.2.3.4");
    }

    @Test
    void xffPresent_multipleIps_returnsLeftmost() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "1.2.3.4, 10.0.0.1, 192.168.1.1").build();
        assertThat(dim.extract(ctx)).isEqualTo("1.2.3.4");
    }

    @Test
    void xffMissing_fallsBackToXRealIp() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Real-IP", "5.6.7.8").build();
        assertThat(dim.extract(ctx)).isEqualTo("5.6.7.8");
    }

    @Test
    void xffEmpty_fallsBackToXRealIp() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "   ")
                .header("X-Real-IP", "5.6.7.8").build();
        assertThat(dim.extract(ctx)).isEqualTo("5.6.7.8");
    }

    @Test
    void allHeadersMissing_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder().build();
        assertThat(dim.extract(ctx)).isNull();
    }

    @Test
    void leftmostIsBlank_returnsNull() {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .header("X-Forwarded-For", "  , 10.0.0.1").build();
        assertThat(dim.extract(ctx)).isNull();
    }
}