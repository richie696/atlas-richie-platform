package com.richie.context.utils.security;

import java.lang.annotation.*;

/**
 * 配置 DTO/VO 签名序列化格式
 * <p>标注在 DTO 类上，覆盖 {@link SignatureUtils} 的默认序列化行为。
 * 默认输出 {@code key=value&key=value} 格式；可通过此注解切换为仅拼接 value 的模式。</p>
 *
 * <pre>{@code
 * // 标准模式（默认）：key=value&key=value
 * @SignConfig
 *
 * // value-only 模式：value1|value2
 * @SignConfig(connector = "|", includeFieldName = false)
 * public class ExternalDTO { ... }
 *
 * // 自定义连接符：key=value,key=value
 * @SignConfig(connector = ",")
 * public class AnotherDTO { ... }
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SignConfig {

    /**
     * 字段之间的连接符
     * <p>默认 {@code "&"}，输出格式为 {@code key=value&key=value}。</p>
     *
     * @return 连接符
     */
    String connector() default "&";

    /**
     * 签名字符串中是否包含字段名
     * <p>为 {@code true} 时输出 {@code key=value} 格式；
     * 为 {@code false} 时仅输出 value，适用于外部接口仅拼接 value 的场景。</p>
     *
     * @return 是否包含字段名
     */
    boolean includeFieldName() default true;
}
