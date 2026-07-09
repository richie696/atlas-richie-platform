/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.circuit;

import com.richie.component.tenant.datasource.DynamicTenantDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 数据源健康探测器。
 *
 * <p>定时探测所有数据源的连接可用性，与 {@link DataSourceCircuitBreaker} 联动：
 * 探测成功 → HALF_OPEN → CLOSED（自动恢复）；探测失败 → 累计 failures。</p>
 *
 * @author richie696
 * @since 2.0
 */
public class DataSourceHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(DataSourceHealthProbe.class);

    private final DynamicTenantDataSource dynamicDataSource;
    private final DataSourceCircuitBreaker circuitBreaker;

    public DataSourceHealthProbe(DynamicTenantDataSource dynamicDataSource,
                                 DataSourceCircuitBreaker circuitBreaker) {
        this.dynamicDataSource = dynamicDataSource;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 无动态数据源的降级构造器（非 DATABASE 模式使用）。
     * 健康探测仅依赖熔断器状态，不主动探测连接。
     */
    public DataSourceHealthProbe(DataSourceCircuitBreaker circuitBreaker) {
        this.dynamicDataSource = null;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 定时探测所有数据源。间隔由 {@code multi-tenancy.health.probe-interval-ms} 控制（默认 30s）。
     */
    @Scheduled(fixedDelayString = "${multi-tenancy.health.probe-interval-ms:30000}")
    public void probeAll() {
        if (dynamicDataSource == null) {
            return;
        }
        probeDataSource("shared", () -> dynamicDataSource.getSharedDataSource());
        for (Map.Entry<String, DataSource> entry : dynamicDataSource.getTenantDataSources().entrySet()) {
            probeDataSource(entry.getKey(), entry::getValue);
        }
    }

    private void probeDataSource(String key, Supplier<DataSource> dataSourceSupplier) {
        DataSourceCircuitBreaker.CircuitStatus status = circuitBreaker.getStatus(key);
        if (status == DataSourceCircuitBreaker.CircuitStatus.CLOSED) {
            return;
        }
        try {
            DataSource ds = dataSourceSupplier.get();
            try (Connection conn = ds.getConnection()) {
                if (conn.isValid(3)) {
                    circuitBreaker.recordSuccess(key);
                } else {
                    circuitBreaker.recordFailure(key);
                }
            }
        } catch (SQLException e) {
            log.debug("Health probe failed for datasource {}: {}", key, e.getMessage());
            circuitBreaker.recordFailure(key);
        }
    }
}
