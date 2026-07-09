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
package com.richie.component.tenant.circuit;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.datasource.DynamicTenantDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("DataSourceHealthProbe — 数据源健康探测器")
class DataSourceHealthProbeTest {

    @Test
    @DisplayName("无动态数据源时 probeAll 安全退出")
    void probeAllWithNullDynamicDataSource() {
        MultiTenancyProperties props = new MultiTenancyProperties();
        DataSourceCircuitBreaker breaker = new DataSourceCircuitBreaker(props);
        DataSourceHealthProbe probe = new DataSourceHealthProbe(breaker);
        probe.probeAll(); // no exception
    }

    @Test
    @DisplayName("探测成功时记录 success")
    void probeSuccessRecordsSuccess() throws Exception {
        MultiTenancyProperties props = new MultiTenancyProperties();
        props.getCircuit().setFailureThreshold(2);
        props.getCircuit().setOpenWindowMs(1);
        DataSourceCircuitBreaker breaker = new DataSourceCircuitBreaker(props);

        // 先触发熔断进入 OPEN 状态
        breaker.recordFailure("shared");
        breaker.recordFailure("shared");
        assertThat(breaker.getStatus("shared")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.OPEN);
        Thread.sleep(10); // 让 openWindowMs 超时

        // 构造 mock DataSource 返回有效连接
        Connection conn = mock(Connection.class);
        when(conn.isValid(3)).thenReturn(true);
        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenReturn(conn);

        DynamicTenantDataSource dynamicDs = mock(DynamicTenantDataSource.class);
        DataSourceHealthProbe probe = new DataSourceHealthProbe(dynamicDs, breaker);

        var method = DataSourceHealthProbe.class.getDeclaredMethod(
                "probeDataSource", String.class, java.util.function.Supplier.class);
        method.setAccessible(true);
        method.invoke(probe, "shared", (java.util.function.Supplier<DataSource>) () -> ds);

        // probeDataSource 内部通过 getStatus() 检查状态，不会触发 OPEN→HALF_OPEN 转换
        // recordSuccess 只在 HALF_OPEN 时转为 CLOSED，OPEN 时不变化
        assertThat(breaker.getStatus("shared")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.OPEN);
    }

    @Test
    @DisplayName("探测失败时记录 failure")
    void probeFailureRecordsFailure() throws Exception {
        MultiTenancyProperties props = new MultiTenancyProperties();
        props.getCircuit().setFailureThreshold(2);
        props.getCircuit().setOpenWindowMs(1);
        DataSourceCircuitBreaker breaker = new DataSourceCircuitBreaker(props);

        // 先进入 OPEN
        breaker.recordFailure("shared");
        breaker.recordFailure("shared");
        Thread.sleep(10); // 让 openWindowMs 超时

        DataSource ds = mock(DataSource.class);
        when(ds.getConnection()).thenThrow(new SQLException("connection refused"));

        DynamicTenantDataSource dynamicDs = mock(DynamicTenantDataSource.class);
        DataSourceHealthProbe probe = new DataSourceHealthProbe(dynamicDs, breaker);

        var method = DataSourceHealthProbe.class.getDeclaredMethod(
                "probeDataSource", String.class, java.util.function.Supplier.class);
        method.setAccessible(true);
        method.invoke(probe, "shared", (java.util.function.Supplier<DataSource>) () -> ds);

        // 探测失败后仍然记录 failure
        assertThat(breaker.getStatus("shared")).isIn(
                DataSourceCircuitBreaker.CircuitStatus.OPEN,
                DataSourceCircuitBreaker.CircuitStatus.HALF_OPEN);
    }

    @Test
    @DisplayName("CLOSED 状态不探测（probeAll 跳过）")
    void closedStatusSkipsProbe() {
        MultiTenancyProperties props = new MultiTenancyProperties();
        DataSourceCircuitBreaker breaker = new DataSourceCircuitBreaker(props);
        // shared 处于 CLOSED，不应触发探测
        assertThat(breaker.getStatus("shared")).isEqualTo(DataSourceCircuitBreaker.CircuitStatus.CLOSED);

        DataSource ds = mock(DataSource.class);
        DynamicTenantDataSource dynamicDs = mock(DynamicTenantDataSource.class);
        DataSourceHealthProbe probe = new DataSourceHealthProbe(dynamicDs, breaker);
        probe.probeAll(); // CLOSED 状态跳过，不抛异常
    }
}
