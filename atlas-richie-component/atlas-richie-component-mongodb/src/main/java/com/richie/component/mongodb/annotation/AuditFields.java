package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体在插入和更新操作时自动填充审计字段。
 * <p>
 * 当一个类被 {@code @AuditFields} 注解时，以下字段会被自动管理：
 * <ul>
 *   <li>{@code createdAt} - 插入时设置为当前时间戳</li>
 *   <li>{@code createdBy} - 插入时设置为当前用户</li>
 *   <li>{@code updatedAt} - 插入和更新时设置为当前时间戳</li>
 *   <li>{@code updatedBy} - 插入和更新时设置为当前用户</li>
 * </ul>
 * <p>
 * 示例：
 * <pre>
 * &#64;AuditFields
 * public class User {
 *     private Instant createdAt;
 *     private String createdBy;
 *     private Instant updatedAt;
 *     private String updatedBy;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AuditFields {
}