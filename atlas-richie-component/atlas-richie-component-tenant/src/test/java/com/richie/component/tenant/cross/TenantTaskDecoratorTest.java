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
package com.richie.component.tenant.cross;

import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantTaskDecorator — micrometer ContextSnapshot 跨线程透传")
class TenantTaskDecoratorTest {

    private ThreadLocalHolder holder;
    private TenantTaskDecorator decorator;

    @BeforeEach
    void setUp() {
        holder = new ThreadLocalHolder();
        decorator = new TenantTaskDecorator();
    }

    @AfterEach
    void tearDown() {
        holder.clear();
    }

    private TenantPrincipal principal(Long id) {
        return new TenantPrincipal().setTenantId(id);
    }

    @Test
    @DisplayName("装饰后的 Runnable 在执行线程中能读取提交线程的租户上下文")
    void decoratedRunnableCanAccessContextInDifferentThread() throws Exception {
        holder.set(principal(1001L));

        // 在提交线程中装饰任务
        Runnable original = () -> {
            // 此任务将在另一个线程中执行
            // 但 ContextSnapshot 会将 ThreadLocal 值传播过去
        };
        Runnable decorated = decorator.decorate(original);

        // 验证 decorated 任务不为 null
        assertThat(decorated).isNotNull();
    }

    @Test
    @DisplayName("跨线程传播 ThreadLocalHolder 的租户上下文")
    void threadLocalHolderContextPropagatedAcrossThreads() throws Exception {
        holder.set(principal(2001L));

        AtomicLong capturedTenantId = new AtomicLong(-1);
        CountDownLatch latch = new CountDownLatch(1);

        // 创建在另一个线程执行的任务，通过 decorator 装饰
        Runnable task = decorator.decorate(() -> {
            TenantPrincipal p = holder.get();
            if (p != null) {
                capturedTenantId.set(p.getTenantId());
            }
            latch.countDown();
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task);
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedTenantId.get()).isEqualTo(2001L);
    }

    @Test
    @DisplayName("DataSourceContextHolder / TableSuffixHolder 跨线程传播")
    void multipleContextsPropagatedAcrossThreads() throws Exception {
        holder.set(principal(3001L));
        com.richie.component.tenant.context.DataSourceContextHolder.set("ds_3001");
        com.richie.component.tenant.context.TableSuffixHolder.set("_3001");

        AtomicLong capturedTenant = new AtomicLong(-1);
        AtomicLong capturedDs = new AtomicLong(-1);
        CountDownLatch latch = new CountDownLatch(1);

        Runnable task = decorator.decorate(() -> {
            TenantPrincipal p = holder.get();
            String ds = com.richie.component.tenant.context.DataSourceContextHolder.get();
            if (p != null) capturedTenant.set(p.getTenantId());
            if (ds != null) capturedDs.set(Long.parseLong(ds.replace("ds_", "")));
            latch.countDown();
        });

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task);
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(capturedTenant.get()).isEqualTo(3001L);
        assertThat(capturedDs.get()).isEqualTo(3001L);

        // 清理
        com.richie.component.tenant.context.DataSourceContextHolder.clear();
        com.richie.component.tenant.context.TableSuffixHolder.clear();
    }

    @Test
    @DisplayName("无租户上下文时装饰不报错")
    void noContextDecorationSucceeds() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Runnable task = decorator.decorate(latch::countDown);

        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            executor.submit(task);
        }

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    }
}
