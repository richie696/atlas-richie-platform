package cn.richie696.component.mcp.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Tool 方法参数的稳定业务元数据。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpArgument {
    String name();

    String description() default "";

    boolean required() default true;
}
