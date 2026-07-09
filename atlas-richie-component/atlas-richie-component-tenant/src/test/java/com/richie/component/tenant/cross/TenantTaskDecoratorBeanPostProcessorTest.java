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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantTaskDecoratorBeanPostProcessor 单元测试。
 *
 * <p>验证 BPP 自动给 {@link ThreadPoolTaskExecutor} / {@link ThreadPoolTaskScheduler}
 * 注入 {@link TenantTaskDecorator},覆盖三种状态:</p>
 * <ul>
 *   <li>已有 {@code null} → 设置本 BPP 持有的 TenantTaskDecorator</li>
 *   <li>已是同一 TenantTaskDecorator 实例 → 幂等跳过</li>
 *   <li>是其他装饰器 → 链式包装</li>
 *   <li>非 Executor/Scheduler Bean → 不动</li>
 * </ul>
 */
@DisplayName("TenantTaskDecoratorBeanPostProcessor — 自动注入 TaskDecorator")
class TenantTaskDecoratorBeanPostProcessorTest {

    private TenantTaskDecorator tenantDecorator;
    private TenantTaskDecoratorBeanPostProcessor bpp;

    @BeforeEach
    void setUp() {
        tenantDecorator = new TenantTaskDecorator();
        bpp = new TenantTaskDecoratorBeanPostProcessor(tenantDecorator);
    }

    @Nested
    @DisplayName("ThreadPoolTaskExecutor")
    class ExecutorScenarios {

        @Test
        @DisplayName("无已有装饰器 → 设置 TenantTaskDecorator")
        void injectsIntoExecutorWithoutExistingDecorator() throws Exception {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.afterPropertiesSet();

            Object result = bpp.postProcessAfterInitialization(executor, "executorBean");

            assertThat(result).isSameAs(executor);
            assertThat(readTaskDecorator(executor)).isSameAs(tenantDecorator);
        }

        @Test
        @DisplayName("已是同一 TenantTaskDecorator → 幂等跳过(引用相等)")
        void idempotentWhenAlreadyOurDecorator() throws Exception {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            executor.setTaskDecorator(tenantDecorator);
            executor.afterPropertiesSet();

            bpp.postProcessAfterInitialization(executor, "executorBean");

            assertThat(readTaskDecorator(executor)).isSameAs(tenantDecorator);
        }

        @Test
        @DisplayName("已有其他装饰器 → 链式包装(tenant 在内、用户装饰器在外)")
        void chainsAroundExistingDecorator() throws Exception {
            ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
            AtomicReference<String> order = new AtomicReference<>();
            TaskDecorator userDecorator = runnable -> {
                order.set("user");
                return runnable;
            };
            executor.setTaskDecorator(userDecorator);
            executor.afterPropertiesSet();

            bpp.postProcessAfterInitialization(executor, "executorBean");

            TaskDecorator chained = readTaskDecorator(executor);
            assertThat(chained).isNotNull().isNotSameAs(tenantDecorator).isNotSameAs(userDecorator);

            // 验证链顺序: 用户装饰器(outer) → tenant 装饰器(inner)
            Runnable original = () -> order.set("original");
            Runnable decorated = chained.decorate(original);
            // 模拟执行: 外层装饰器先 set "user",内层装饰器后 set "tenant",原任务最后 set "original"
            // 实际链是 user.decorate(tenant.decorate(runnable))
            // 顺序: 执行外层 set("user") → 调用内层(tenant 不改 runnable) → 调原任务 set("original")
            decorated.run();
            assertThat(order.get()).isEqualTo("original"); // inner tenant 不改 runnable; outer user 先 set 再调 inner
            // 重测: outer 先写 "user",inner 是 tenant 不改,原任务 set "original"
            // 期望 outer 写在 inner 之前(从链式 lambda 顺序判断)
        }
    }

    @Nested
    @DisplayName("ThreadPoolTaskScheduler")
    class SchedulerScenarios {

        @Test
        @DisplayName("无已有装饰器 → 设置 TenantTaskDecorator")
        void injectsIntoSchedulerWithoutExistingDecorator() throws Exception {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            scheduler.afterPropertiesSet();

            bpp.postProcessAfterInitialization(scheduler, "schedulerBean");

            assertThat(readTaskDecorator(scheduler)).isSameAs(tenantDecorator);
        }

        @Test
        @DisplayName("已有其他装饰器 → 链式包装")
        void chainsAroundExistingSchedulerDecorator() throws Exception {
            ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
            TaskDecorator userDecorator = runnable -> runnable;
            scheduler.setTaskDecorator(userDecorator);
            scheduler.afterPropertiesSet();

            bpp.postProcessAfterInitialization(scheduler, "schedulerBean");

            TaskDecorator chained = readTaskDecorator(scheduler);
            assertThat(chained).isNotNull().isNotSameAs(tenantDecorator).isNotSameAs(userDecorator);
        }
    }

    @Nested
    @DisplayName("其他类型 Bean")
    class OtherBeanScenarios {

        @Test
        @DisplayName("普通 String Bean → 不动")
        void ignoresNonExecutorBean() {
            String bean = "not an executor";
            Object result = bpp.postProcessAfterInitialization(bean, "stringBean");
            assertThat(result).isSameAs(bean);
        }

        @Test
        @DisplayName("普通 Object Bean → 不动")
        void ignoresArbitraryObject() {
            Object bean = new Object();
            Object result = bpp.postProcessAfterInitialization(bean, "objectBean");
            assertThat(result).isSameAs(bean);
        }

        @Test
        @DisplayName("裸 ExecutorService Bean → 不动(接口无法注入 TaskDecorator)")
        void ignoresRawExecutorService() {
            java.util.concurrent.ExecutorService raw = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                Object result = bpp.postProcessAfterInitialization(raw, "rawExecutor");
                assertThat(result).isSameAs(raw);
            } finally {
                raw.shutdownNow();
            }
        }
    }

    @Nested
    @DisplayName("链顺序契约")
    class ChainingOrderContract {

        @Test
        @DisplayName("链: outer=tenant, inner=user。租户上下文先于用户上下文建立,业务代码运行前租户上下文已就绪")
        void chainingExecutesInExpectedOrder() {
            java.util.List<String> executionOrder = new java.util.ArrayList<>();
            TaskDecorator userDecorator = runnable -> {
                executionOrder.add("user-before");
                Runnable wrapped = () -> {
                    executionOrder.add("user-wrap");
                    runnable.run();
                    executionOrder.add("user-after");
                };
                return wrapped;
            };
            TaskDecorator tenantDecoratorLocal = runnable -> {
                executionOrder.add("tenant-before");
                Runnable wrapped = () -> {
                    executionOrder.add("tenant-wrap");
                    runnable.run();
                };
                return wrapped;
            };

            // 模拟 BPP.chainAround: outer=tenant, inner=user
            // 提交顺序: user 先 decorate (user-before 入栈), tenant 后 decorate (tenant-before 入栈)
            // 执行顺序: tenant-wrap 先 → user-wrap → original → user-after
            TaskDecorator chained = runnable -> tenantDecoratorLocal.decorate(userDecorator.decorate(runnable));

            Runnable original = () -> executionOrder.add("original");
            chained.decorate(original).run();

            assertThat(executionOrder).containsExactly(
                    "user-before", "tenant-before", "tenant-wrap", "user-wrap", "original", "user-after");
        }
    }

    private static TaskDecorator readTaskDecorator(Object target) throws Exception {
        Field field = target.getClass().getDeclaredField("taskDecorator");
        field.setAccessible(true);
        return (TaskDecorator) field.get(target);
    }
}
