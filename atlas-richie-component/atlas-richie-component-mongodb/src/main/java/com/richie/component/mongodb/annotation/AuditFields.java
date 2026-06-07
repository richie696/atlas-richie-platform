package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity to automatically populate audit fields on insert and update operations.
 * <p>
 * When a class is annotated with {@code @AuditFields}, the following fields are automatically managed:
 * <ul>
 *   <li>{@code createdAt} - set to current timestamp on insert</li>
 *   <li>{@code createdBy} - set to current user on insert</li>
 *   <li>{@code updatedAt} - set to current timestamp on insert and update</li>
 *   <li>{@code updatedBy} - set to current user on insert and update</li>
 * </ul>
 * <p>
 * Example:
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