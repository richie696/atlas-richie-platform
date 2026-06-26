package com.richie.component.tenant.web;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.contract.constant.GlobalConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("TenantIdentityFilter — 租户身份识别过滤器")
class TenantIdentityFilterTest {

    private MultiTenancyProperties props;
    private TenantInfoProvider provider;
    private TenantIdentityFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;
    private StringWriter responseWriter;

    @BeforeEach
    void setUp() throws Exception {
        props = new MultiTenancyProperties();
        props.setMicroservice(false); // 避免通信框架诊断日志

        provider = new TenantInfoProvider() {
            @Override
            public TenantInfo getTenantInfo(Long tenantId) {
                if (tenantId == 1001L) {
                    return new TenantInfo()
                            .setTenantId(1001L)
                            .setMode(IsolationMode.COLUMN)
                            .setStatus(TenantStatus.ACTIVE);
                }
                if (tenantId == 9999L) {
                    return null; // 未知租户
                }
                if (tenantId == 8888L) {
                    return new TenantInfo()
                            .setTenantId(8888L)
                            .setMode(IsolationMode.COLUMN)
                            .setStatus(TenantStatus.EXPIRED);
                }
                if (tenantId == 7777L) {
                    return new TenantInfo()
                            .setTenantId(7777L)
                            .setMode(IsolationMode.COLUMN)
                            .setStatus(TenantStatus.MIGRATING);
                }
                return null;
            }

            @Override
            public boolean exists(Long tenantId) {
                return tenantId == 1001L;
            }
        };

        filter = new TenantIdentityFilter(props, provider, List.of("/health", "/public/**"),
            List.of("/platform-admin/**"));
        TenantContext.init(new ThreadLocalHolder());

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Nested
    @DisplayName("功能开关")
    class FeatureToggle {

        @Test
        @DisplayName("enabled=false 时直接放行")
        void disabledPassesThrough() throws Exception {
            props.setEnabled(false);
            when(request.getRequestURI()).thenReturn("/api/orders");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(any(Integer.class));
        }
    }

    @Nested
    @DisplayName("白名单路径")
    class WhitelistPaths {

        @Test
        @DisplayName("精确匹配 /health 跳过绑定")
        void exactMatchSkips() throws Exception {
            when(request.getRequestURI()).thenReturn("/health");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("/public/** 通配符匹配")
        void wildcardMatchSkips() throws Exception {
            when(request.getRequestURI()).thenReturn("/public/docs/api");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Nested
    @DisplayName("Header 解析（Feign 内部调用）")
    class HeaderParsing {

        @Test
        @DisplayName("X-Tenant-ID header → 绑定租户上下文")
        void xTenantIdHeaderBindsContext() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("1001");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("无效 X-Tenant-ID（非数字）+ enforceAuthTenant=false → 超管放行")
        void invalidHeaderPassesThrough() throws Exception {
            props.setEnforceAuthTenant(false);
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("not-a-number");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("无租户信息 + enforceAuthTenant=false → 超管放行")
        void noTenantInfoPassesThrough() throws Exception {
            props.setEnforceAuthTenant(false);
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn(null);
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
        }

        @Test
        @DisplayName("无效 X-Tenant-ID + enforceAuthTenant=true → 拒绝(401)")
        void invalidHeaderRejectedWhenEnforced() throws Exception {
            // 默认 enforceAuthTenant=true
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("not-a-number");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("无租户信息 + enforceAuthTenant=true → 拒绝(401)")
        void noTenantInfoRejectedWhenEnforced() throws Exception {
            // 默认 enforceAuthTenant=true
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn(null);
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }

        @Test
        @DisplayName("超管专用路径 + 无租户信息 + enforceAuthTenant=true → 放行")
        void superAdminPathBypassesEnforcement() throws Exception {
            // 默认 enforceAuthTenant=true
            when(request.getRequestURI()).thenReturn("/platform-admin/users");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn(null);
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(request, response);
            verify(response, never()).setStatus(any(Integer.class));
        }
    }

    @Nested
    @DisplayName("租户校验")
    class TenantValidation {

        @Test
        @DisplayName("未知租户 → 403 TENANT_IDENTITY_NOT_FOUND")
        void unknownTenantReturns403() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("9999");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_IDENTITY_NOT_FOUND");
        }

        @Test
        @DisplayName("过期租户 → 403 TENANT_AUTH_EXPIRED")
        void expiredTenantReturns403() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("8888");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_AUTH_EXPIRED");
        }

        @Test
        @DisplayName("迁移中租户 → 503 TENANT_MIGRATING")
        void migratingTenantReturns503() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("7777");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_MIGRATING");
        }

        @Test
        @DisplayName("tenantId <= 0 → 403 TENANT_AUTH_INVALID_FORMAT")
        void invalidTenantIdReturns403() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("0");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_AUTH_INVALID_FORMAT");
        }
    }

    @Nested
    @DisplayName("Header 交叉校验")
    class CrossValidation {

        @Test
        @DisplayName("X-Tenant-ID 与 JWT tenantId 不一致 → 403 TENANT_AUTH_MISMATCH")
        void mismatchReturns403() throws Exception {
            // 使用 header-only 模式（无 JWT），X-Tenant-ID 与 header 中解析的值交叉校验
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            // 第一次 getHeader(X-Tenant-ID) 返回 1001（用于解析 principal）
            // 第二次 getHeader(X-Tenant-ID) 返回 2001（用于交叉校验，故意不一致）
            when(request.getHeader(props.getTenantIdHeader()))
                    .thenReturn("1001")  // resolveFromHeader 使用
                    .thenReturn("2001"); // 交叉校验使用
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("2001");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_AUTH_MISMATCH");
        }
    }
}
