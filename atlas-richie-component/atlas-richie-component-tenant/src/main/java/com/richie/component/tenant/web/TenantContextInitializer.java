package com.richie.component.tenant.web;

import com.richie.component.tenant.context.TenantContextHolder;
import com.richie.contract.model.TenantPrincipal;
import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.JwtUtils;
import jakarta.annotation.Nonnull;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 租户上下文初始化拦截器
 *
 * <p>优先从 {@code X-ACCESS-TOKEN} 请求头（JWT 令牌）中解析完整的
 * {@link TenantPrincipal}（含 tenantId、tenantName、expiredTime），
 * 降级策略为从 {@code X-Tenant-Id} 请求头中读取租户 ID。</p>
 *
 * <p>请求完成后自动清理上下文，防止内存泄漏。</p>
 *
 * <p>此拦截器运行在微服务侧（Servlet 容器），Gateway（WebFlux）不通过此方式处理。</p>
 *
 * @author richie696
 * @since 1.0
 */
public class TenantContextInitializer implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextInitializer.class);

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);
        if (token != null && !token.isEmpty()) {
            TenantPrincipal tenant = JwtUtils.getTenantPrincipal(token);
            if (tenant != null) {
                TenantContextHolder.set(tenant);
                return true;
            }
        }

        String tenantIdStr = request.getHeader(GlobalConstants.X_TENANT_ID);
        if (tenantIdStr == null || tenantIdStr.isEmpty()) {
            return true;
        }

        try {
            Long tenantId = Long.valueOf(tenantIdStr);
            TenantPrincipal tenant = new TenantPrincipal();
            tenant.setTenantId(tenantId);
            TenantContextHolder.set(tenant);
        } catch (NumberFormatException e) {
            log.warn("无效的租户 ID header: {} = '{}', 跳过租户设置",
                    GlobalConstants.X_TENANT_ID, tenantIdStr);
        }

        return true;
    }

    @Override
    public void afterCompletion(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response,
                                @Nonnull Object handler, Exception ex) {
        TenantContextHolder.clear();
    }
}
