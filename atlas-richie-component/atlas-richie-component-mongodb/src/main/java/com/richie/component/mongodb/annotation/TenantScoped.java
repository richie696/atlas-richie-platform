package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity as tenant-scoped, automatically filtering queries by tenant ID.
 * <p>
 * When a class is annotated with {@code @TenantScoped}, all query operations will
 * automatically include a tenant ID filter based on the current {@link com.richie.component.mongodb.core.TenantContext}.
 * This ensures data isolation between tenants.
 * <p>
 * Example:
 * <pre>
 * &#64;TenantScoped("tenantId")
 * public class User {
 *     private String tenantId;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TenantScoped {

    /**
     * The field name used to store the tenant identifier.
     *
     * @return the field name, defaults to "tenantId"
     */
    String value() default "tenantId";
}