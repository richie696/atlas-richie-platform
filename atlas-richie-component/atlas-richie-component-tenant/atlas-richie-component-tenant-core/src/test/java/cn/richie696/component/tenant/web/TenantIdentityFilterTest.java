/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.tenant.web;

import cn.richie696.component.tenant.config.MultiTenancyProperties;
import cn.richie696.component.tenant.context.TenantContext;
import cn.richie696.component.tenant.context.ThreadLocalHolder;
import cn.richie696.component.tenant.model.IsolationMode;
import cn.richie696.component.tenant.model.TenantInfo;
import cn.richie696.component.tenant.model.TenantStatus;
import cn.richie696.component.tenant.spi.TenantInfoProvider;
import cn.richie696.contract.constant.GlobalConstants;
import cn.richie696.contract.exception.BusinessException;
import cn.richie696.context.utils.spring.TenantIdentityAssertionUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
        props.setEnable(true);
        props.setMicroservice(false); // 避免通信框架诊断日志
        // 这些测试覆盖遗留内部 header 兼容路径；生产默认值为 false。
        props.setAllowUnsignedTenantHeader(true);

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
                if (tenantId == 5555L) {
                    return new TenantInfo()
                            .setTenantId(5555L)
                            .setMode(IsolationMode.COLUMN)
                            .setStatus(TenantStatus.INACTIVE);
                }
                if (tenantId == 6666L) {
                    return new TenantInfo()
                            .setTenantId(6666L)
                            .setMode(IsolationMode.COLUMN)
                            .setStatus(TenantStatus.PROVISIONING);
                }
                if (tenantId == 4444L) {
                    return new TenantInfo()
                            .setTenantId(4444L)
                            .setMode(IsolationMode.COLUMN)
                            // 显式不设置 status —— 用于覆盖 status == null 分支
                            ;
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
            props.setEnable(false);
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

    @Nested
    @DisplayName("内部身份断言")
    class IdentityAssertion {

        @Test
        @DisplayName("有效 Gateway 断言可在未开启未签名 Header 时建立上下文")
        void validAssertionBindsTenant() throws Exception {
            props.setAllowUnsignedTenantHeader(false);
            props.getGateway().setIdentityAssertionSecret("secret");
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_TENANT_ASSERTION))
                    .thenReturn(TenantIdentityAssertionUtils.create(1001L,
                            System.currentTimeMillis() + 60_000, "secret"));
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("未签名 Header 默认不建立租户上下文")
        void unsignedHeaderRejectedByDefault() throws Exception {
            props.setAllowUnsignedTenantHeader(false);
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("1001");

            filter.doFilterInternal(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            verify(response).setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }

    @Nested
    @DisplayName("异常解包")
    class ExceptionUnwrapping {

        @Test
        @DisplayName("ServletException 解包 BusinessException")
        void servletExceptionUnwrapsBusinessException() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("1001");

            BusinessException be = new BusinessException("TENANT_BUSINESS_ERROR", "业务异常");
            doThrow(new ServletException(be)).when(chain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("业务异常");
        }

        @Test
        @DisplayName("IOException 包装为 RuntimeException")
        void ioExceptionWrappedInRuntimeException() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("1001");

            doThrow(new IOException("IO error")).when(chain).doFilter(any(), any());

            assertThatThrownBy(() -> filter.doFilterInternal(request, response, chain))
                    .isInstanceOf(RuntimeException.class)
                    .hasCauseInstanceOf(IOException.class);
        }
    }

    @Nested
    @DisplayName("覆盖率补全")
    class AdditionalCoverage {

        @Test
        @DisplayName("3 参构造器委托给 4 参构造器（覆盖 L98-L99）")
        void threeArgConstructorDelegates() throws Exception {
            TenantIdentityFilter threeArg =
                    new TenantIdentityFilter(props, provider, List.of("/health", "/public/**"));
            // 委托后，3 参与 4 参版本行为相同——重新跑一次精确匹配白名单验证
            HttpServletRequest req = mock(HttpServletRequest.class);
            HttpServletResponse resp = mock(HttpServletResponse.class);
            FilterChain ch = mock(FilterChain.class);
            when(req.getRequestURI()).thenReturn("/health");
            threeArg.doFilter(req, resp, ch);

            verify(ch).doFilter(req, resp);
        }

        @Test
        @DisplayName("INACTIVE 状态租户 → 403 TENANT_AUTH_INACTIVE（覆盖 L181-L183）")
        void inactiveTenantReturns403() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("5555");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_AUTH_INACTIVE");
        }

        @Test
        @DisplayName("PROVISIONING 状态租户 → 503 TENANT_PROVISIONING（覆盖 L185-L187）")
        void provisioningTenantReturnsServiceUnavailable() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("6666");

            filter.doFilterInternal(request, response, chain);

            // TENANT_PROVISIONING 在 TenantErrorCode 中实际定义为 503（SERVICE_UNAVAILABLE）
            verify(response).setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_PROVISIONING");
        }

        @Test
        @DisplayName("status == null → 403 TENANT_AUTH_INACTIVE（覆盖 L181 第一个条件）")
        void nullStatusReturns403() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("4444");

            filter.doFilterInternal(request, response, chain);

            verify(response).setStatus(HttpStatus.FORBIDDEN.value());
            verify(chain, never()).doFilter(any(), any());
            assertThat(responseWriter.toString()).contains("TENANT_AUTH_INACTIVE");
        }

        @Test
        @DisplayName("JWT verify 返回 false → resolveFromJwt 返回 null 后 fallback（覆盖 L207-L209）")
        void jwtVerifyFalseFallsBack() throws Exception {
            // 重新构造 filter 注入 jwtSecret；setUp 内的 props 默认 jwtSecret=null
            props.setJwtSecret("secret-key");
            filter = new TenantIdentityFilter(props, provider,
                    List.of("/health", "/public/**"), List.of("/platform-admin/**"));
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn("not.a.valid.jwt");
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn("not.a.valid.jwt");
            // header 1001 触发 resolveFromHeader fallback（allowUnsignedTenantHeader=true）
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");

            filter.doFilterInternal(request, response, chain);

            // principal=1001 经过 active 校验后被绑定，chain 应被调用
            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("JWT token 为 null → resolveFromJwt 走 token-null fallback（覆盖 L232-L234）")
        void jwtTokenNullFallsThrough() throws Exception {
            props.setJwtSecret("secret-key");
            filter = new TenantIdentityFilter(props, provider,
                    List.of("/health", "/public/**"), List.of("/platform-admin/**"));
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            when(request.getHeader(props.getTenantIdHeader())).thenReturn("1001");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn("1001");

            filter.doFilterInternal(request, response, chain);

            verify(chain).doFilter(any(), any());
        }

        @Test
        @DisplayName("Header 交叉校验遇到 NFE → log.warn 后正常放行（覆盖 L202-L205）")
        void headerCrossCheckNumberFormatLogsAndContinues() throws Exception {
            when(request.getRequestURI()).thenReturn("/api/orders");
            when(request.getHeader(GlobalConstants.X_ACCESS_TOKEN)).thenReturn(null);
            when(request.getHeader("X-ACCESS-TOKEN")).thenReturn(null);
            // resolveFromHeader 第一次读到 "1001" → principal；交叉校验再读到 "not-a-number" → NFE
            when(request.getHeader(props.getTenantIdHeader()))
                    .thenReturn("1001")
                    .thenReturn("not-a-number");
            when(request.getHeader(GlobalConstants.X_TENANT_ID)).thenReturn(null);

            filter.doFilterInternal(request, response, chain);

            // 异常被吞掉，请求继续放行（principal=1001 有效）
            verify(chain).doFilter(any(), any());
        }
    }
}
