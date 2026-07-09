/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.cross;

import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;

/**
 * 租户感知的 {@link StructuredTaskScope} 工厂。
 *
 * <p>基于 Java 25 {@link StructuredTaskScope},封装常用 Joiner 的工厂方法。
 * 在子任务 {@code fork()} 时,ScopedValue 自动继承 — 因此
 * 租户上下文(TenantContext)、数据源路由(DataSourceContextHolder)、表名后缀
 * (TableSuffixHolder)等全部 ThreadLocal-backed 状态对子任务可见,
 * 不需要额外装饰器。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 1) 等待全部子任务成功
 * try (var scope = TenantStructuredTaskScope.awaitAll()) {
 *     var f1 = scope.fork(() -> userService.findById(1L));
 *     var f2 = scope.fork(() -> orderService.findByUser(1L));
 *     scope.join();
 *     User user = f1.get();
 *     List<Order> orders = f2.get();
 * }
 *
 * // 2) 等任一成功即返回
 * try (var scope = TenantStructuredTaskScope.anySuccessful()) {
 *     var f1 = scope.fork(() -> primaryQuery());
 *     var f2 = scope.fork(() -> backupQuery());
 *     scope.join();
 *     return f1.get();
 * }
 * }</pre>
 *
 * <h2>异常传递规范</h2>
 * <ul>
 *   <li>{@code awaitAllSuccessfulOrThrow}: 任一子任务异常立即终止 scope,异常透传</li>
 *   <li>{@code anySuccessfulResultOrThrow}: 首个成功结果返回,其他子任务的异常被忽略</li>
 *   <li>{@code allSuccessfulOrThrow}: 全部成功才返回,任一异常即抛</li>
 * </ul>
 *
 * <h2>为什么不直接用 {@code StructuredTaskScope.open(...)}</h2>
 * <p>本工厂统一封装 Joiner 语义 + 提供租户相关示例,业务方无需记忆 {@code awaitAllSuccessfulOrThrow}
 * 等较长的 API 名称。</p>
 *
 * @author richie696
 * @since 1.0.0
 */
public final class TenantStructuredTaskScope {

    private TenantStructuredTaskScope() {
    }

    /**
     * 创建{@link Joiner#awaitAllSuccessfulOrThrow() awaitAllSuccessfulOrThrow} Joiner 的 scope —
     * 等待所有子任务成功;任一抛异常立即终止并透传异常。
     *
     * @param <T> 子任务返回类型
     * @return 新的 StructuredTaskScope 实例,需 try-with-resources 关闭
     */
    public static <T> StructuredTaskScope<T, Void> awaitAll() {
        return StructuredTaskScope.open(Joiner.<T>awaitAllSuccessfulOrThrow());
    }

    /**
     * 创建{@link Joiner#anySuccessfulResultOrThrow() anySuccessfulResultOrThrow} Joiner 的 scope —
     * 首个成功结果返回,其余子任务异常被忽略。
     *
     * @param <T> 子任务返回类型
     * @return 新的 StructuredTaskScope 实例,需 try-with-resources 关闭
     */
    public static <T> StructuredTaskScope<T, T> anySuccessful() {
        return StructuredTaskScope.open(Joiner.<T>anySuccessfulResultOrThrow());
    }
}
