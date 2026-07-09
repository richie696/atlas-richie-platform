/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * MongoDB 断路器的默认降级方法。
 * <p>
 * 当 Sentinel 触发降级（慢请求比例超标）时，
 * 这些方法返回安全的空结果，而不是传播异常。
 * <p>
 * 设计决策：对于返回构建器的方法（query/update/delete），
 * 我们返回一个特殊的"降级"构建器，在终止操作时返回安全的空结果。
 * 这使得流式 API 在降级时仍能保持链式调用。
 *
 * @author richie696
 * @version 1.0
 * @since 2026-06-07
 */
public final class DefaultFallbacks {

    private DefaultFallbacks() {
    }

    /**
     * 查询操作的降级方法 - 返回空列表。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 空列表
     */
    public static <T> List<T> query(Class<T> entityClass) {
        return Collections.emptyList();
    }

    /**
     * 计数操作的降级方法 - 返回零。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 0L
     */
    public static <T> Long count(Class<T> entityClass) {
        return 0L;
    }

    /**
     * one() 操作的降级方法 - 返回 null。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return null
     */
    public static <T> T one(Class<T> entityClass) {
        return null;
    }

    /**
     * oneOpt() 操作的降级方法 - 返回空 Optional。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return Optional.empty()
     */
    public static <T> Optional<T> oneOpt(Class<T> entityClass) {
        return Optional.empty();
    }

    /**
     * pageResult() 操作的降级方法 - 返回空 PageResult。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 空 PageResult
     */
    public static <T> PageResult<T> pageResult(Class<T> entityClass) {
        return new PageResult<>(Collections.emptyList(), 0, 1, 0);
    }

    /**
     * update.execute() 操作的降级方法 - 返回 0L（无文档被修改）。
     *
     * @return 0L
     */
    public static long updateExecute() {
        return 0L;
    }

    /**
     * update.executeAndReturn() 操作的降级方法 - 返回 null。
     *
     * @param <T> 实体类型
     * @return null
     */
    public static <T> T updateExecuteAndReturn() {
        return null;
    }

    /**
     * delete.execute() 操作的降级方法 - 返回 0L（无文档被删除）。
     *
     * @return 0L
     */
    public static long deleteExecute() {
        return 0L;
    }

    /**
     * save 操作的降级方法 - 返回 null。
     *
     * @param <T> 实体类型
     * @return null
     */
    public static <T> T save() {
        return null;
    }

    /**
     * insert 操作的降级方法 - 返回 null。
     *
     * @param <T> 实体类型
     * @return null
     */
    public static <T> T insert() {
        return null;
    }

    /**
     * insertAll 操作的降级方法 - 返回空列表。
     *
     * @param <T> 实体类型
     * @return 空列表
     */
    public static <T> List<T> insertAll() {
        return Collections.emptyList();
    }

    /**
     * findById 操作的降级方法 - 返回空 Optional。
     *
     * @param <T> 实体类型
     * @return Optional.empty()
     */
    public static <T> Optional<T> findById() {
        return Optional.empty();
    }

    /**
     * existsById 操作的降级方法 - 返回 false。
     *
     * @return false
     */
    public static boolean existsById() {
        return false;
    }

    /**
     * deleteById 操作的降级方法 - 返回 false（不是错误，只是无操作）。
     *
     * @return false
     */
    public static boolean deleteById() {
        return false;
    }

    /**
     * dropCollection 操作的降级方法 - 返回 false（不是错误，只是无操作）。
     *
     * @return false
     */
    public static boolean dropCollection() {
        return false;
    }

    /**
     * 返回一个在终止操作时返回空列表的降级 QueryBuilder。
     * <p>
     * 这使得流式 API 在降级时仍能保持链式调用：
     * <pre>
     * mongodb.query(Entity.class).eq(...).list()  // 降级时返回空列表
     * </pre>
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 返回空结果的 QueryBuilder
     */
    public static <T> QueryBuilder<T> queryBuilder(Class<T> entityClass) {
        return new DegradedQueryBuilder<>();
    }

    /**
     * 返回一个在执行时返回 0 的降级 UpdateBuilder。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 返回 0 的 UpdateBuilder
     */
    public static <T> UpdateBuilder<T> updateBuilder(Class<T> entityClass) {
        return new DegradedUpdateBuilder<>();
    }

    /**
     * 返回一个在执行时返回 0 的降级 DeleteBuilder。
     *
     * @param entityClass 实体类（降级时忽略）
     * @param <T>        实体类型
     * @return 返回 0 的 DeleteBuilder
     */
    public static <T> DeleteBuilder<T> deleteBuilder(Class<T> entityClass) {
        return new DegradedDeleteBuilder<>();
    }

    /**
     * 在终止操作时返回空列表的降级 QueryBuilder。
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
     * 在执行时返回 0 的降级 UpdateBuilder。
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
     * 在执行时返回 0 的降级 DeleteBuilder。
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
