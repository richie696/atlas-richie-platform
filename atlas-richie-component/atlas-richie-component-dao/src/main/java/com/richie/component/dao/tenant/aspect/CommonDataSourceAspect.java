package com.richie.component.dao.tenant.aspect;

import com.richie.component.dao.tenant.TenantConstant;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * 通用数据源切面：对标注 @CommonDataSource 的方法切换至 common 表所在数据源后执行。
 *
 * @author yuy
 * @version 1.0
 * @since 2023-10-04
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class CommonDataSourceAspect {

    @Pointcut("@annotation(com.richie.component.dao.tenant.annotation.CommonDataSource)")
    public void cutMethod() {
    }

    /**
     * 切换默认数据源
     * @param pjp 切点
     * @return 返回切点执行结果
     * @throws Throwable 异常
     */
    @Around("cutMethod()")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        try {
            DynamicDataSourceContextHolder.push(TenantConstant.MASTER_DS_NAME);
            return pjp.proceed();
        } finally {
            DynamicDataSourceContextHolder.poll();
        }
    }

}
