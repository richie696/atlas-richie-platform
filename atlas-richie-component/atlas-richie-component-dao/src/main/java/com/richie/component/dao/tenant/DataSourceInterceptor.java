package com.richie.component.dao.tenant;

import com.richie.contract.constant.GlobalConstants;
import com.richie.context.utils.spring.JwtUtils;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 数据源拦截器：在请求进入时从 Token/Header 解析租户编码并设置数据源上下文，请求结束后清理。
 *
 * @author 于跃
 * @version 1.0
 * @since 2023-09-18
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class DataSourceInterceptor implements HandlerInterceptor {

    /** 租户数据源属性缓存，用于根据租户与 @DS 解析数据源 key */
    private final TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache;

    /** {@inheritDoc} */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader(JwtUtils.X_ACCESS_TOKEN);
        String finalTenantCode;
        if (StringUtils.isBlank(token)) {
            finalTenantCode = getHeaderTenantCode(request);
        } else {
            String tenantCode = JwtUtils.getTenantCode(token);
            if (StringUtils.isBlank(tenantCode)) {
                log.error("tenantCode is null");
                finalTenantCode = getHeaderTenantCode(request);
            } else {
                finalTenantCode = tenantCode;
            }
        }
        log.info("tenantCode：{}", finalTenantCode);
        TenantCodeContextHolder.setTenantCode(Long.parseLong(finalTenantCode));
        String datasourceKey = tenantDataSourcePropertyMapCache.getDatasourceKey();
        if (datasourceKey != null) {
            DynamicDataSourceContextHolder.push(datasourceKey);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        TenantCodeContextHolder.clearContext();
        DynamicDataSourceContextHolder.clear();
    }

    private String getHeaderTenantCode(HttpServletRequest request) {
        String headerTenantCode = request.getHeader(GlobalConstants.X_TENANT_CODE_TOKEN);
        if (StringUtils.isBlank(headerTenantCode)) {
            log.error("headerTenantCode is null");
        } else {
            return headerTenantCode;
        }
        return "0";
    }

}
