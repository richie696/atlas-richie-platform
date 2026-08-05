package cn.richie696.component.mcp.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将业务方法声明为 MCP Tool。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTool {
    String name() default "";

    String title() default "";

    String description() default "";

    boolean idempotent() default false;
}
