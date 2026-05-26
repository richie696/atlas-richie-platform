package com.richie.component.logging.annotations;

import java.lang.annotation.*;

/**
 * 类型追踪日志标签
 *
 * @author richie696
 * @version 1.0
 * @since 2025-01-03 17:06:46
 */
@Documented
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogTrace {

    /**
     * 日志标签
     *
     * @return 日志标签
     */
    String value() default "";

}
