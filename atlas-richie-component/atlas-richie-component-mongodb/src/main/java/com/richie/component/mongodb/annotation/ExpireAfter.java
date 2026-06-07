package com.richie.component.mongodb.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field to expire (TTL index) after the specified number of seconds.
 * <p>
 * When a field is annotated with {@code @ExpireAfter}, MongoDB will automatically
 * remove documents when the field's value exceeds the specified TTL.
 * The field should be of type {@link java.time.Instant} or {@link java.util.Date}.
 * <p>
 * Example:
 * <pre>
 * public class User {
 *     &#64;ExpireAfter(seconds = 3600)
 *     private Instant resetTokenExpiry;
 * }
 * </pre>
 *
 * @author Richie
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExpireAfter {

    /**
     * The number of seconds after which the document should expire.
     *
     * @return the TTL in seconds
     */
    long seconds();
}