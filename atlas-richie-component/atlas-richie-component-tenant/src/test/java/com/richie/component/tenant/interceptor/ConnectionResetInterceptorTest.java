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
package com.richie.component.tenant.interceptor;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.DataSourceContextHolder;
import com.richie.component.tenant.context.TableSuffixHolder;
import org.apache.ibatis.plugin.Invocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ConnectionResetInterceptor — SQL 执行后清理线程局部变量")
class ConnectionResetInterceptorTest {

    private MultiTenancyProperties props;
    private ConnectionResetInterceptor interceptor;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        interceptor = new ConnectionResetInterceptor(props);
    }

    @AfterEach
    void tearDown() {
        DataSourceContextHolder.clear();
        TableSuffixHolder.clear();
    }

    @Test
    @DisplayName("SQL 执行后 DataSourceContextHolder 和 TableSuffixHolder 被清理")
    void clearsContextAfterSqlExecution() throws Throwable {
        DataSourceContextHolder.set("ds_1001");
        TableSuffixHolder.set("_1001");

        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn("result");

        Object result = interceptor.intercept(invocation);

        assertThat(result).isEqualTo("result");
        assertThat(DataSourceContextHolder.get()).isNull();
        assertThat(TableSuffixHolder.get()).isNull();
    }

    @Test
    @DisplayName("SQL 执行抛异常后仍然清理上下文")
    void clearsContextEvenOnException() throws Throwable {
        DataSourceContextHolder.set("ds_1001");
        TableSuffixHolder.set("_1001");

        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenThrow(new RuntimeException("db error"));

        try {
            interceptor.intercept(invocation);
        } catch (RuntimeException ignored) {
        }

        assertThat(DataSourceContextHolder.get()).isNull();
        assertThat(TableSuffixHolder.get()).isNull();
    }

    @Test
    @DisplayName("enabled=false 时不清理（但 proceed 仍执行）")
    void disabledSkipsCleanup() throws Throwable {
        props.setEnabled(false);
        DataSourceContextHolder.set("ds_1001");

        Invocation invocation = mock(Invocation.class);
        when(invocation.proceed()).thenReturn("ok");

        interceptor.intercept(invocation);

        // enabled=false 时不清理
        assertThat(DataSourceContextHolder.get()).isEqualTo("ds_1001");
    }
}
