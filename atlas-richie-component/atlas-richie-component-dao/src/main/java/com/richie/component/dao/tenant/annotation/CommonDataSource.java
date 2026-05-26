package com.richie.component.dao.tenant.annotation;

import java.lang.annotation.*;

/**
 * 通用数据源切换注解：标注在方法上，执行时切换到 common 表所在数据源。
 *
 * @author yuyue
 * @version 1.0
 * @since 2023-10-04
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CommonDataSource {

}
