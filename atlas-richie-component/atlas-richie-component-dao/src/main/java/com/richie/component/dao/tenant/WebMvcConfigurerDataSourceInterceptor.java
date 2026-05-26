package com.richie.component.dao.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 租户数据源拦截器配置
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-04 18:12:08
 */
@RequiredArgsConstructor
public class WebMvcConfigurerDataSourceInterceptor implements WebMvcConfigurer {

    /** 租户数据源属性缓存，用于注入到 DataSourceInterceptor */
    private final TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache;

    /**
     * 添加拦截器
     *
     * @param interceptorRegistry 拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry interceptorRegistry) {
        interceptorRegistry.addInterceptor(new DataSourceInterceptor(tenantDataSourcePropertyMapCache));
    }

}

