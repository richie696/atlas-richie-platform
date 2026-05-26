package com.richie.component.logging.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 是否启用访问切面日志
 *
 * @author richie696
 * @version 1.0
 * @since 2022-01-16 10:54:48
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessLog {

    /**
     * 日志描述
     *
     * @return 日志描述
     */
    String value() default "";

    /**
     * 是否持久化
     *
     * @return 是否持久化
     */
    boolean persistent() default false;

}
