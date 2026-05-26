package com.richie.component.logging.annotations;

import com.richie.component.logging.enums.LogLevelEnum;

import java.lang.annotation.*;

/**
 * 方法追踪日志标签
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-03 17:06:46
 */
@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogMethodTrace {

    /**
     * 日志标签
     *
     * @return 日志标签
     */
    String value() default "";

    /**
     * 日志级别
     *
     * @return 日志级别（默认：INFO）
     */
    LogLevelEnum level() default LogLevelEnum.INFO;

    /**
     * 是否忽略方法参数
     *
     * @return 是否忽略方法参数（true：忽略[默认]，false：不忽略）
     */
    boolean ignoreArgs() default true;

    /**
     * 是否忽略方法返回值
     *
     * @return 是否忽略方法返回值（true：忽略[默认]，false：不忽略）
     */
    boolean ignoreResult() default true;
}
