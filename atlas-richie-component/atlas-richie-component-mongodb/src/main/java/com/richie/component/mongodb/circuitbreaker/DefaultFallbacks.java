package com.richie.component.mongodb.circuitbreaker;

import com.richie.component.mongodb.builder.DeleteBuilder;
import com.richie.component.mongodb.builder.PageResult;
import com.richie.component.mongodb.builder.QueryBuilder;
import com.richie.component.mongodb.builder.UpdateBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Default fallback methods for MongoDB circuit breaker.
 * <p>
 * When Sentinel triggers a degrade (slow request ratio exceeded),
 * these methods return safe empty results instead of propagating the exception.
 * <p>
 * Design decision: For builder-returning methods (query/update/delete),
 * we return a special "degraded" builder that returns safe empty results
 * on terminal operations. This allows the fluent API to remain chainable
 * even when degraded.
 *
 * @author richie696
 * @version 1.0
 * @since 2026-06-07
 */
public final class DefaultFallbacks {

    private DefaultFallbacks() {
    }

    /**
     * Fallback for query operations - returns empty list.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return empty list
     */
    public static <T> List<T> query(Class<T> entityClass) {
        return Collections.emptyList();
    }

    /**
     * Fallback for count operations - returns zero.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return 0L
     */
    public static <T> Long count(Class<T> entityClass) {
        return 0L;
    }

    /**
     * Fallback for one() operations - returns null.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return null
     */
    public static <T> T one(Class<T> entityClass) {
        return null;
    }

    /**
     * Fallback for oneOpt() operations - returns empty Optional.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return Optional.empty()
     */
    public static <T> Optional<T> oneOpt(Class<T> entityClass) {
        return Optional.empty();
    }

    /**
     * Fallback for pageResult() operations - returns empty PageResult.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return empty PageResult
     */
    public static <T> PageResult<T> pageResult(Class<T> entityClass) {
        return new PageResult<>(Collections.emptyList(), 0, 1, 0);
    }

    /**
     * Fallback for update.execute() operations - returns 0L (no documents modified).
     *
     * @return 0L
     */
    public static long updateExecute() {
        return 0L;
    }

    /**
     * Fallback for update.executeAndReturn() operations - returns null.
     *
     * @param <T> the entity type
     * @return null
     */
    public static <T> T updateExecuteAndReturn() {
        return null;
    }

    /**
     * Fallback for delete.execute() operations - returns 0L (no documents deleted).
     *
     * @return 0L
     */
    public static long deleteExecute() {
        return 0L;
    }

    /**
     * Fallback for save operations - returns null.
     *
     * @param <T> the entity type
     * @return null
     */
    public static <T> T save() {
        return null;
    }

    /**
     * Fallback for insert operations - returns null.
     *
     * @param <T> the entity type
     * @return null
     */
    public static <T> T insert() {
        return null;
    }

    /**
     * Fallback for insertAll operations - returns empty list.
     *
     * @param <T> the entity type
     * @return empty list
     */
    public static <T> List<T> insertAll() {
        return Collections.emptyList();
    }

    /**
     * Fallback for findById operations - returns empty Optional.
     *
     * @param <T> the entity type
     * @return Optional.empty()
     */
    public static <T> Optional<T> findById() {
        return Optional.empty();
    }

    /**
     * Fallback for existsById operations - returns false.
     *
     * @return false
     */
    public static boolean existsById() {
        return false;
    }

    /**
     * Fallback for deleteById operations - returns false (not an error, just no-op).
     *
     * @return false
     */
    public static boolean deleteById() {
        return false;
    }

    /**
     * Fallback for dropCollection operations - returns false (not an error, just no-op).
     *
     * @return false
     */
    public static boolean dropCollection() {
        return false;
    }

    /**
     * Returns a degraded QueryBuilder that returns empty list on terminal operations.
     * <p>
     * This allows the fluent API to remain chainable when degraded:
     * <pre>
     * mongodb.query(Entity.class).eq(...).list()  // returns empty list when degraded
     * </pre>
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return a QueryBuilder that returns empty results
     */
    public static <T> QueryBuilder<T> queryBuilder(Class<T> entityClass) {
        return new DegradedQueryBuilder<>();
    }

    /**
     * Returns a degraded UpdateBuilder that returns 0 on execute.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return an UpdateBuilder that returns 0
     */
    public static <T> UpdateBuilder<T> updateBuilder(Class<T> entityClass) {
        return new DegradedUpdateBuilder<>();
    }

    /**
     * Returns a degraded DeleteBuilder that returns 0 on execute.
     *
     * @param entityClass the entity class (ignored in fallback)
     * @param <T>        the entity type
     * @return a DeleteBuilder that returns 0
     */
    public static <T> DeleteBuilder<T> deleteBuilder(Class<T> entityClass) {
        return new DegradedDeleteBuilder<>();
    }

    /**
     * Degraded QueryBuilder that returns empty list on terminal operations.
     */
    private static class DegradedQueryBuilder<T> extends QueryBuilder<T> {
        DegradedQueryBuilder() {
            super(null, null, null);
        }

        @Override
        public List<T> list() {
            return Collections.emptyList();
        }

        @Override
        public T one() {
            return null;
        }

        @Override
        public Optional<T> oneOpt() {
            return Optional.empty();
        }

        @Override
        public T oneOrThrow(java.util.function.Supplier<? extends RuntimeException> exSupplier) {
            return null;
        }

        @Override
        public long count() {
            return 0L;
        }

        @Override
        public PageResult<T> pageResult() {
            return new PageResult<>(Collections.emptyList(), 0, 1, 0);
        }
    }

    /**
     * Degraded UpdateBuilder that returns 0 on execute.
     */
    private static class DegradedUpdateBuilder<T> extends UpdateBuilder<T> {
        DegradedUpdateBuilder() {
            super(null, null, null);
        }

        @Override
        public long execute() {
            return 0L;
        }

        @Override
        public T executeAndReturn() {
            return null;
        }
    }

    /**
     * Degraded DeleteBuilder that returns 0 on execute.
     */
    private static class DegradedDeleteBuilder<T> extends DeleteBuilder<T> {
        DegradedDeleteBuilder() {
            super(null, null, null);
        }

        @Override
        public long execute() {
            return 0L;
        }
    }
}
