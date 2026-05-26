package com.richie.component.dao.tenant;

import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 在 MyBatis-Plus 拦截器链中注入租户行级隔离插件，并保证分页插件在最后执行。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-09-13
 */
@RequiredArgsConstructor
public class ModifyMybatisPlusInterceptor {

    /** MyBatis-Plus 拦截器 */
    private final MybatisPlusInterceptor mybatisPlusInterceptor;

    /** 租户行级内部拦截器 */
    private final TenantLineInnerInterceptor tenantLineInnerInterceptor;

    @PostConstruct
    public void mybatisPlusInnerInterceptorBeanPostProcessor() {
        mybatisPlusInterceptor.addInnerInterceptor(tenantLineInnerInterceptor);
        //判断是否有 PaginationInnerInterceptor 并且放到最后
        List<InnerInterceptor> interceptors = mybatisPlusInterceptor.getInterceptors();//不可修改Collections.unmodifiableList
        List<InnerInterceptor> newList = new ArrayList<>();
        PaginationInnerInterceptor paginationInnerInterceptor = null;
        for (InnerInterceptor interceptor : interceptors) {
            if (interceptor instanceof PaginationInnerInterceptor) {
                paginationInnerInterceptor = (PaginationInnerInterceptor) interceptor;
            } else {
                newList.add(interceptor);
            }
        }
        if (paginationInnerInterceptor != null) {
            newList.add(paginationInnerInterceptor);
        }
        mybatisPlusInterceptor.setInterceptors(newList);
    }

}
