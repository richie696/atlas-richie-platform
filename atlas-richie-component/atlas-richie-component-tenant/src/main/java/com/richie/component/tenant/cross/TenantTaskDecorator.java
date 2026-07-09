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

import io.micrometer.context.ContextSnapshot;
import io.micrometer.context.ContextSnapshotFactory;
import jakarta.annotation.Nonnull;
import org.springframework.core.task.TaskDecorator;

/**
 * Spring 线程池租户上下文装饰器。
 *
 * <p>基于 micrometer {@code context-propagation} 的 {@link ContextSnapshot}，
 * 在任务提交时捕获当前线程的所有已注册 {@code ThreadLocalAccessor} 值，
 * 在任务执行时恢复。覆盖租户上下文、数据源路由、表名后缀等全部上下文。</p>
 *
 * <p>自动覆盖 {@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、虚拟线程等场景。</p>
 * <h2>使用方式</h2>
 * <pre>{@code
 * @Bean
 * public TaskExecutor taskExecutor() {
 *     ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
 *     executor.setTaskDecorator(new TenantTaskDecorator());
 *     // ... 其他配置
 *     return executor;
 * }
 * }</pre>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantTaskDecorator implements TaskDecorator {

    private static final ContextSnapshotFactory SNAPSHOT_FACTORY =
            ContextSnapshotFactory.builder().build();

    /**
     * 装饰 {@link Runnable} 使其执行时自动恢复租户上下文。
     *
     * <p>在任务<b>提交线程</b>中通过 micrometer {@link ContextSnapshot} 捕获所有
     * 已注册的 {@code ThreadLocalAccessor}（包括 TenantContext / DataSourceContextHolder
     * / TableSuffixHolder），返回的 Runnable 在<b>执行线程</b>中自动恢复快照。</p>
     *
     * <p>适配 {@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、
     * 虚拟线程等所有跨线程场景。Spring {@code ThreadPoolTaskExecutor} 配合
     * {@code TenantTaskDecoratorBeanPostProcessor} 可全自动织入。</p>
     *
     * @param runnable 原始任务
     * @return 装饰后的任务,执行前自动恢复租户上下文
     */
    /**
     * 装饰 {@link Runnable} 使其执行时自动恢复租户上下文。
     *
     * <p>在任务提交线程中通过 micrometer {@link ContextSnapshot} 捕获所有已注册的
     * {@code ThreadLocalAccessor}(包括 TenantContext / DataSourceContextHolder /
     * TableSuffixHolder),返回的 Runnable 在执行线程中自动恢复快照。</p>
     *
     * <p>覆盖 {@code @Async}、{@code CompletableFuture}、{@code @Scheduled}、虚拟线程
     * 等所有跨线程场景。Spring {@code ThreadPoolTaskExecutor} 配合
     * {@code TenantTaskDecoratorBeanPostProcessor} 可全自动织入。</p>
     *
     * @param runnable 原始任务
     * @return 装饰后的任务,执行前自动恢复租户上下文
     */
    @Nonnull
    @Override
    public Runnable decorate(@Nonnull Runnable runnable) {
        // 在提交线程中捕获所有已注册的 ThreadLocal 上下文快照
        ContextSnapshot snapshot = SNAPSHOT_FACTORY.captureAll();
        // 在执行线程中恢复上下文
        return snapshot.wrap(runnable);
    }
}
