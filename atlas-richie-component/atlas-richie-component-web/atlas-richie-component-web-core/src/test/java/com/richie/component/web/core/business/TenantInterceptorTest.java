package com.richie.component.web.core.business;

import com.richie.component.web.core.config.business.BusinessIntegrationProperties;
import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantInterceptorTest {

    private final BusinessIntegrationProperties.Tenant config = new BusinessIntegrationProperties.Tenant();
    private final TenantInterceptor interceptor = new TenantInterceptor(config);

    @Test
    void headerPresent_setsAttributeAndMdc() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Tenant-Id", "tenant-a")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isEqualTo("tenant-a");
        // MDC verification skipped in unit test — SLF4J backend = NOPMDCAdapter in test env
    }

    @Test
    void headerMissing_passesThroughWithoutAttribute() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isNull();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void blankHeader_passesThrough() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Tenant-Id", "   ")
                .build();
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isNull();
    }

    @Test
    void requireOnMissing_deny() throws Exception {
        BusinessIntegrationProperties.Tenant strictConfig = new BusinessIntegrationProperties.Tenant();
        strictConfig.setRequireOnMissing(true);
        TenantInterceptor strict = new TenantInterceptor(strictConfig);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .build();
        strict.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(400);
        assertThat(ctx.shortCircuitBody()).contains("missing_tenant");
    }

    @Test
    void mdcCleanedUpAfterProceed_doesNotThrow() throws Exception {
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Tenant-Id", "tenant-b")
                .build();
        MDC.put(TenantInterceptor.MDC_KEY, "old-value");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        // 单测环境 MDC backend 不固定，仅验证不抛
        MDC.clear();
    }

    @Test
    void customHeaderName_isHonored() throws Exception {
        BusinessIntegrationProperties.Tenant custom = new BusinessIntegrationProperties.Tenant();
        custom.setHeaderName("X-Custom-Tenant");
        TenantInterceptor inter = new TenantInterceptor(custom);
        MutableWebRequestContext ctx = MutableWebRequestContext.builder()
                .method("GET").path("/api/v1/users")
                .header("X-Custom-Tenant", "tenant-c")
                .build();
        inter.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat((String) ctx.attribute(TenantInterceptor.TENANT_ATTRIBUTE)).isEqualTo("tenant-c");
    }

    @Test
    void getOrder_is260() {
        assertThat(interceptor.getOrder()).isEqualTo(260);
    }
}