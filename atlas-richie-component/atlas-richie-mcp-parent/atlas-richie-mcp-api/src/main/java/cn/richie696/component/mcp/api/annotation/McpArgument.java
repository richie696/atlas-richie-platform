package cn.richie696.component.mcp.api.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明 Tool 方法参数的稳定业务元数据。
 *
 * <p>该注解由 {@code atlas-richie-mcp-server} 在启动时扫描，并结合 {@link McpTool} 一起转换为
 * 协议层的 JSON Schema（{@code type/format/minimum/maximum/enum/...}）。它故意设计为不直接绑定
 * JSON Schema 字段名，而是使用语义化的"约束名"——这样底层协议从 v2024 升级到 v2025 时，
 * 业务代码无需修改。</p>
 *
 * <p>关键设计：</p>
 * <ul>
 *   <li>{@link #NO_DEFAULT} 使用 {@code "\u0000"} 作为哨兵值，使得"未提供默认值"和"默认值为空字符串"
 *       可以被可靠区分；</li>
 *   <li>{@link #sensitive()} 在审计与日志阶段用于把该字段的取值替换为 {@code ***}，防止敏感数据外泄。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpArgument {
    /**
     * 哨兵值：标识 {@link #defaultValue()} 显式未设置。
     *
     * <p>使用 {@code "\u0000"}（空字符）作为不可见哨兵，使得"无默认值"与"默认值为空字符串"
     * 在序列化层可被严格区分，避免语义模糊。</p>
     */
    String NO_DEFAULT = "\u0000";

    /**
     * 参数业务名，缺省时回落到 Java 参数名。
     *
     * @return 参数名
     */
    String name() default "";

    /**
     * 业务描述，会被写入 JSON Schema 的 {@code description} 字段。
     *
     * @return 描述文本
     */
    String description() default "";

    /**
     * 是否必填。
     *
     * @return {@code true} 表示必填
     */
    boolean required() default true;

    /**
     * 默认值，未设置时为 {@link #NO_DEFAULT}。
     *
     * @return 默认值字符串
     */
    String defaultValue() default NO_DEFAULT;

    /**
     * JSON Schema 的 {@code format}（如 {@code date-time}/{@code uri}/{@code email}）。
     *
     * @return format 字符串
     */
    String format() default "";

    /**
     * 示例值，会出现在 JSON Schema 的 {@code examples} 字段，便于 LLM 理解。
     *
     * @return 示例字符串
     */
    String example() default "";

    /**
     * 枚举值，约束参数取值集合。
     *
     * @return 候选值数组
     */
    String[] enumValues() default {};

    /**
     * 数值下界（JSON Schema {@code minimum}）。
     *
     * @return 下界字符串，缺省表示不约束
     */
    String minimum() default "";

    /**
     * 数值上界（JSON Schema {@code maximum}）。
     *
     * @return 上界字符串，缺省表示不约束
     */
    String maximum() default "";

    /**
     * 字符串最小长度（JSON Schema {@code minLength}）。
     *
     * @return 最小长度，{@code -1} 表示不约束
     */
    int minLength() default -1;

    /**
     * 字符串最大长度（JSON Schema {@code maxLength}）。
     *
     * @return 最大长度，{@code -1} 表示不约束
     */
    int maxLength() default -1;

    /**
     * Marks the value for audit/log redaction.
     *
     * <p>审计与日志组件会扫描该标记，把字段实际取值替换为 {@code ***}，以避免口令、Token、
     * 身份证号等敏感数据进入日志与审计存储。</p>
     *
     * @return {@code true} 表示该参数是敏感字段
     */
    boolean sensitive() default false;
}
