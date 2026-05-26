package com.richie.component.dao.tenant;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.dynamic.datasource.support.DataSourceClassResolver;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import jakarta.annotation.Nonnull;


/**
 * 动态数据源自定义注解拦截：根据 @DS 注解解析数据源 key 并切换上下文，供多租户多数据源使用。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-21
 */
@Slf4j
public class DynamicDataSourceCustomAnnotationInterceptor implements MethodInterceptor {

    /** 数据源类解析器，用于解析 @DS 注解 */
    private final DataSourceClassResolver dataSourceClassResolver;

    /** 租户数据源属性缓存 */
    private final TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache;

    /**
     * 初始化拦截器
     *
     * @param allowedPublicOnly              是否仅允许 public 方法参与解析
     * @param tenantDataSourcePropertyMapCache 租户数据源属性缓存
     */
    public DynamicDataSourceCustomAnnotationInterceptor(Boolean allowedPublicOnly, TenantDataSourcePropertyMapCache tenantDataSourcePropertyMapCache) {
        this.dataSourceClassResolver = new DataSourceClassResolver(allowedPublicOnly);
        this.tenantDataSourcePropertyMapCache = tenantDataSourcePropertyMapCache;
    }

    /**
     * 执行目标方法前根据 @DS 注解切换数据源，执行后恢复
     *
     * @param invocation 方法调用
     * @return 目标方法返回值
     * @throws Throwable 目标方法或 proceed 抛出的异常
     */
    @Override
    public Object invoke(@Nonnull MethodInvocation invocation) throws Throwable {
        String annotationKey = getAnnotationKey(invocation);
        String dsKey = tenantDataSourcePropertyMapCache.getDatasourceKey(annotationKey);
        DynamicDataSourceContextHolder.push(dsKey);
        try {
            return invocation.proceed();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

    /**
     * 从方法或类上解析 @DS 注解的 key
     *
     * @param invocation 方法调用
     * @return @DS 注解中的 key，未指定时可能为 null
     */
    private String getAnnotationKey(MethodInvocation invocation) {
        return dataSourceClassResolver.findKey(invocation.getMethod(), invocation.getThis(), DS.class);
    }

}
