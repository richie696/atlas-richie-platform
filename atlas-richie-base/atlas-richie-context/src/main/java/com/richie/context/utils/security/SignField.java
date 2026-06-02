package com.richie.context.utils.security;

import java.lang.annotation.*;

/**
 * 标记 DTO/VO 中参与签名计算的字段
 * <p>
 * 配合 {@link SignatureUtils#createSign(Object, String, String)} 使用。
 * 默认按字段名首字母排序；可通过 {@link #order()} 指定排序数值（从小到大）。
 * 集合类型（Collection/Map）不允许标注此注解。
 * </p>
 *
 * <pre>{@code
 * public class OrderDTO {
 *     @SignField(order = 2)
 *     private String name;
 *     @SignField(order = 1)
 *     private int age;
 *     @SignField
 *     private String email; // order=0，排在 name/age 之后，按字母序
 *     private String notSigned; // 不参与签名
 * }
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SignField {

    /**
     * 排序序号，从小到大排列
     * <p>相同 order 值的字段按字段名首字母（字典序）排列。</p>
     *
     * @return 排序序号
     */
    int order() default 0;

    /**
     * 自定义参与签名的字段名
     * <p>默认使用 Java 字段名（驼峰）；当不同平台签名规范不一致时（如 snake_case），
     * 可通过此属性指定签名使用的字段名称。</p>
     *
     * @return 自定义签名字段名，为空则使用 Java 字段名
     */
    String name() default "";
}
