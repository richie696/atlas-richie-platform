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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.model.IsolationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TenancyStrategyFactory — 策略工厂")
class TenancyStrategyFactoryTest {

    private MultiTenancyProperties props() {
        return new MultiTenancyProperties();
    }

    private com.richie.component.tenant.spi.TenantInfoProvider noop() {
        return new com.richie.component.tenant.spi.TenantInfoProvider() {
            @Override public com.richie.component.tenant.model.TenantInfo getTenantInfo(Long tenantId) { return null; }
            @Override public boolean exists(Long tenantId) { return false; }
        };
    }

    @Test
    @DisplayName("构造时注册所有 5 种策略")
    void allFiveStrategiesRegistered() {
        TenancyStrategyFactory factory = new TenancyStrategyFactory(List.of(
                new ColumnStrategy(props(), noop()),
                new TableStrategy(props(), noop()),
                new SchemaStrategy(props(), noop()),
                new DatabaseStrategy(props(), noop()),
                stubHybrid()
        ));

        for (IsolationMode mode : IsolationMode.values()) {
            assertThat(factory.getStrategy(mode)).isNotNull();
        }
    }

    @Test
    @DisplayName("缺少某策略时构造抛出 IllegalArgumentException")
    void missingStrategyThrows() {
        assertThatThrownBy(() -> new TenancyStrategyFactory(List.of(
                new ColumnStrategy(props(), noop())
                // 缺少 TABLE, SCHEMA, DATABASE, HYBRID
        ))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("getStrategy(COLUMN) 返回 ColumnStrategy")
    void getColumnStrategy() {
        TenancyStrategyFactory factory = new TenancyStrategyFactory(List.of(
                new ColumnStrategy(props(), noop()),
                new TableStrategy(props(), noop()),
                new SchemaStrategy(props(), noop()),
                new DatabaseStrategy(props(), noop()),
                stubHybrid()
        ));

        assertThat(factory.getStrategy(IsolationMode.COLUMN)).isInstanceOf(ColumnStrategy.class);
        assertThat(factory.getStrategy(IsolationMode.TABLE)).isInstanceOf(TableStrategy.class);
        assertThat(factory.getStrategy(IsolationMode.SCHEMA)).isInstanceOf(SchemaStrategy.class);
        assertThat(factory.getStrategy(IsolationMode.DATABASE)).isInstanceOf(DatabaseStrategy.class);
        assertThat(factory.getStrategy(IsolationMode.HYBRID)).isInstanceOf(HybridStrategy.class);
    }

    private HybridStrategy stubHybrid() {
        return new HybridStrategy(props(), noop(),
                new ColumnStrategy(props(), noop()),
                new TableStrategy(props(), noop()),
                new SchemaStrategy(props(), noop()),
                new DatabaseStrategy(props(), noop()));
    }
}
