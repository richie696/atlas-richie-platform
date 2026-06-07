package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity for soft delete behavior.
 * <p>
 * When a class is annotated with {@code @SoftDelete}, delete operations will set the specified
 * field to {@code true} instead of physically removing the document. Query operations will
 * automatically filter out soft-deleted documents unless explicitly bypassed.
 * <p>
 * Example:
 * <pre>
 * &#64;SoftDelete("deleted")
 * public class User {
 *     private Boolean deleted;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SoftDelete {

    /**
     * The field name used to track soft delete status.
     *
     * @return the field name, defaults to "deleted"
     */
    String value() default "deleted";
}