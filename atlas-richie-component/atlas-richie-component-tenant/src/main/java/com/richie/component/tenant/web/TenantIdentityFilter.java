package com.richie.component.tenant.web;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.contract.constant.GlobalConstants;
import com.richie.contract.model.TenantPrincipal;
import com.richie.context.utils.spring.JwtUtils;
import com.richie.context.utils.data.JsonUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 租户身份识别 Filter（替代旧 {@code TenantContextInitializer}）。
 *
 * <p>整个 {@code chain.doFilter} 包裹在 {@link TenantContext#runWithTenant} 内，
 * 保证请求生命周期内的租户上下文一致性（ScopedValue / TTL 双模式兼容）。</p>
 *
 * <h2>解析流程</h2>
 * <ol>
 *   <li>白名单路径 → 跳过租户绑定</li>
 *   <li>从 JWT（{@code X-ACCESS-TOKEN}）解析 {@link TenantPrincipal}</li>
 *   <li>降级：从 {@code X-Tenant-ID} header 读取（Feign 内部调用场景）</li>
 *   <li>校验 tenantId 合法性（Long 正整数）</li>
 *   <li>通过 {@link TenantInfoProvider} 查询租户是否存在、是否过期</li>
 *   <li>JWT tenantId 与 X-Tenant-ID header 交叉校验（防伪造）</li>
 *   <li>{@code TenantContext.runWithTenant(principal, () -> chain.doFilter(...))}</li>
 * </ol>
 *
 * <h2>超级管理员</h2>
 * <p>JWT 中无 tenantId claim → 视为平台超管，不绑定租户上下文，直接放行。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantIdentityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantIdentityFilter.class);

    private final MultiTenancyProperties properties;
    private final TenantInfoProvider tenantInfoProvider;

    /**
     * 白名单路径列表（如健康检查、公开 API），匹配时跳过租户绑定。
     */
    private final List<String> whitelistPaths;

    public TenantIdentityFilter(MultiTenancyProperties properties,
                                TenantInfoProvider tenantInfoProvider,
                                List<String> whitelistPaths) {
        this.properties = properties;
        this.tenantInfoProvider = tenantInfoProvider;
        this.whitelistPaths = whitelistPaths != null ? whitelistPaths : List.of();
    }

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request,
                                    @Nonnull HttpServletResponse response,
                                    @Nonnull FilterChain filterChain)
        throws ServletException, IOException {

        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();

        // 白名单检查
        if (isWhitelisted(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 1. 从 JWT 解析租户
        TenantPrincipal principal = resolveFromJwt(request);

        // 2. 降级：从 X-Tenant-ID header 解析（Feign 内部调用）
        if (principal == null) {
            principal = resolveFromHeader(request);
        }

        // 3. 超管或无租户信息 → 直接放行
        if (principal == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Long tenantId = principal.getTenantId();

        // 4. 校验 tenantId 合法性
        if (tenantId <= 0) {
            writeError(response, TenantErrorCode.TENANT_AUTH_INVALID_FORMAT, tenantId);
            return;
        }

        // 5. 查询租户信息（存在性 + 过期 + 迁移中检查）
        TenantInfo tenantInfo = tenantInfoProvider.getTenantInfo(tenantId);
        if (tenantInfo == null) {
            writeError(response, TenantErrorCode.TENANT_IDENTITY_NOT_FOUND, tenantId);
            return;
        }

        if (tenantInfo.getStatus() == TenantStatus.EXPIRED) {
            writeError(response, TenantErrorCode.TENANT_AUTH_EXPIRED, tenantId);
            return;
        }

        if (tenantInfo.getStatus() == TenantStatus.MIGRATING) {
            writeError(response, TenantErrorCode.TENANT_MIGRATING, tenantId);
            return;
        }

        // 6. JWT tenantId 与 X-Tenant-ID header 交叉校验
        String headerTenantId = request.getHeader(properties.getTenantIdHeader());
        if (headerTenantId == null) {
            headerTenantId = request.getHeader(GlobalConstants.X_TENANT_ID);
        }
        if (headerTenantId != null && !headerTenantId.isEmpty()) {
            try {
                Long headerId = Long.valueOf(headerTenantId);
                if (!tenantId.equals(headerId)) {
                    writeError(response, TenantErrorCode.TENANT_AUTH_MISMATCH, tenantId, headerId);
                    return;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid X-Tenant-ID header value: {}", headerTenantId);
            }
        }

        // 7. 绑定租户上下文并执行请求（runWithTenant 嵌套模式）
        final TenantPrincipal boundPrincipal = principal;
        TenantContext.runWithTenant(boundPrincipal, () -> {
            try {
                filterChain.doFilter(request, response);
            } catch (ServletException | IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 从 JWT 令牌解析租户主体。
     */
    private TenantPrincipal resolveFromJwt(HttpServletRequest request) {
        String token = request.getHeader(GlobalConstants.X_ACCESS_TOKEN);
        if (token == null || token.isEmpty()) {
            token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);
        }
        if (token == null || token.isEmpty()) {
            return null;
        }

        try {
            TenantPrincipal principal = JwtUtils.getTenantPrincipal(token);
            if (principal != null && principal.getTenantId() != null) {
                return principal;
            }
        } catch (Exception e) {
            log.debug("Failed to parse tenant from JWT: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 从 X-Tenant-ID header 降级解析租户。
     */
    private TenantPrincipal resolveFromHeader(HttpServletRequest request) {
        String tenantIdStr = request.getHeader(properties.getTenantIdHeader());
        if (tenantIdStr == null || tenantIdStr.isEmpty()) {
            tenantIdStr = request.getHeader(GlobalConstants.X_TENANT_ID);
        }
        if (tenantIdStr == null || tenantIdStr.isEmpty()) {
            return null;
        }

        try {
            Long tenantId = Long.valueOf(tenantIdStr);
            return new TenantPrincipal().setTenantId(tenantId);
        } catch (NumberFormatException e) {
            log.warn("Invalid tenant ID header value: {} = '{}'",
                properties.getTenantIdHeader(), tenantIdStr);
            return null;
        }
    }

    /**
     * 检查请求路径是否在白名单中。
     */
    private boolean isWhitelisted(String requestUri) {
        for (String path : whitelistPaths) {
            if (requestUri.startsWith(path) || matchSimplePattern(requestUri, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单模式匹配（支持 * 通配符）。
     */
    private boolean matchSimplePattern(String uri, String pattern) {
        if (pattern.endsWith("/**")) {
            return uri.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return uri.startsWith(prefix) && !uri.substring(prefix.length()).contains("/");
        }
        return uri.equals(pattern);
    }

    /**
     * 写入 JSON 错误响应。
     */
    private void writeError(HttpServletResponse response, TenantErrorCode errorCode,
                            Object... args) throws IOException {
        response.setStatus(errorCode.getHttpStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", errorCode.getCode());
        body.put("msg", errorCode.format(args));
        body.put("timestamp", System.currentTimeMillis());
        body.put("data", null);

        String json = JsonUtils.getInstance().serialize(body);
        response.getWriter().write(json != null ? json : "{}");
    }
}
