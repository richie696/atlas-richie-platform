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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ExecutorConfigurationSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;

/**
 * BeanPostProcessor — 自动给所有 Spring {@link ThreadPoolTaskExecutor} /
 * {@link ThreadPoolTaskScheduler} Bean 注入 {@link TenantTaskDecorator}。
 *
 * <h2>背景</h2>
 * <p>{@link org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor}
 * 默认不携带 {@link TaskDecorator},{@code @Async} 任务提交后子线程租户上下文丢失
 * （silent failure,无任何日志,租户上下文 {@code null}）。手动给每个 Executor
 * 配置 {@code setTaskDecorator(new TenantTaskDecorator())} 容易遗漏。</p>
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>Bean 初始化后检查: 反射读取 {@code ExecutorConfigurationSupport.taskDecorator} 字段</li>
 *   <li>已有 {@code null} → 设置本 BPP 持有的 {@link TenantTaskDecorator} 单例</li>
 *   <li>已是同一 {@link TenantTaskDecorator} 实例 → 跳过(幂等)</li>
 *   <li>是其他装饰器 → 链式包装(tenant 在内、用户装饰器在外)</li>
 * </ul>
 *
 * <h2>适用范围</h2>
 * <p>仅 Spring 原生 {@link ThreadPoolTaskExecutor} 和 {@link ThreadPoolTaskScheduler};
 * 裸 {@link java.util.concurrent.ExecutorService} 接口 Bean 无法注入 TaskDecorator,
 * 需用户自行包装。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class TenantTaskDecoratorBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(TenantTaskDecoratorBeanPostProcessor.class);

    private final TenantTaskDecorator tenantTaskDecorator;

    public TenantTaskDecoratorBeanPostProcessor(TenantTaskDecorator tenantTaskDecorator) {
        this.tenantTaskDecorator = tenantTaskDecorator;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ThreadPoolTaskExecutor executor) {
            injectIntoExecutor(executor, beanName);
        } else if (bean instanceof ThreadPoolTaskScheduler scheduler) {
            injectIntoScheduler(scheduler, beanName);
        }
        return bean;
    }

    private void injectIntoExecutor(ThreadPoolTaskExecutor executor, String beanName) {
        try {
            TaskDecorator existing = readExistingTaskDecorator(executor);
            if (existing == null) {
                executor.setTaskDecorator(tenantTaskDecorator);
                log.debug("Injected TenantTaskDecorator into ThreadPoolTaskExecutor '{}'", beanName);
            } else if (existing == tenantTaskDecorator) {
                log.trace("TenantTaskDecorator already present on ThreadPoolTaskExecutor '{}'", beanName);
            } else {
                executor.setTaskDecorator(chainAround(existing));
                log.debug("Chained TenantTaskDecorator with existing TaskDecorator on ThreadPoolTaskExecutor '{}'",
                        beanName);
            }
        } catch (Exception e) {
            log.warn("Failed to inject TenantTaskDecorator into ThreadPoolTaskExecutor '{}': {}",
                    beanName, e.getMessage());
        }
    }

    private void injectIntoScheduler(ThreadPoolTaskScheduler scheduler, String beanName) {
        try {
            TaskDecorator existing = readExistingTaskDecorator(scheduler);
            if (existing == null) {
                scheduler.setTaskDecorator(tenantTaskDecorator);
                log.debug("Injected TenantTaskDecorator into ThreadPoolTaskScheduler '{}'", beanName);
            } else if (existing == tenantTaskDecorator) {
                log.trace("TenantTaskDecorator already present on ThreadPoolTaskScheduler '{}'", beanName);
            } else {
                scheduler.setTaskDecorator(chainAround(existing));
                log.debug("Chained TenantTaskDecorator with existing TaskDecorator on ThreadPoolTaskScheduler '{}'",
                        beanName);
            }
        } catch (Exception e) {
            log.warn("Failed to inject TenantTaskDecorator into ThreadPoolTaskScheduler '{}': {}",
                    beanName, e.getMessage());
        }
    }

    /**
     * 把 {@link TenantTaskDecorator} 套在已有装饰器外层。
     *
     * <p>顺序: {@code tenantTaskDecorator.decorate(existing.decorate(runnable))}。
     * 任务提交时,existing 先包裹原任务(捕获用户上下文),tenant 再包裹(捕获租户上下文);
     * 任务执行时,tenant 外层先恢复租户上下文,existing 内层再恢复用户上下文,最后执行原任务。
     * 这样业务代码在租户上下文已建立的环境中运行,避免 silent null。</p>
     */
    private TaskDecorator chainAround(TaskDecorator existing) {
        return runnable -> tenantTaskDecorator.decorate(existing.decorate(runnable));
    }

    private static TaskDecorator readExistingTaskDecorator(Object target) throws Exception {
        // Spring 7 将 taskDecorator 字段从 ExecutorConfigurationSupport 移到 ThreadPoolTaskExecutor /
        // ThreadPoolTaskScheduler 各自定义,需用运行时类取字段,不能用基类。
        Field field = target.getClass().getDeclaredField("taskDecorator");
        field.setAccessible(true);
        return (TaskDecorator) field.get(target);
    }
}
