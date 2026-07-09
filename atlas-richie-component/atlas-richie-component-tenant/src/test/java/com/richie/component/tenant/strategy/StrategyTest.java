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
package com.richie.component.tenant.strategy;

import com.richie.component.tenant.config.MultiTenancyProperties;
import com.richie.component.tenant.context.DataSourceContextHolder;
import com.richie.component.tenant.context.TableSuffixHolder;
import com.richie.component.tenant.context.TenantContext;
import com.richie.component.tenant.context.ThreadLocalHolder;
import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.TenantErrorCode;
import com.richie.component.tenant.model.IsolationMode;
import com.richie.component.tenant.model.TenantInfo;
import com.richie.component.tenant.model.TenantStatus;
import com.richie.component.tenant.spi.TenantInfoProvider;
import com.richie.contract.model.TenantPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("隔离策略 — supports + beforeSqlExecute")
class StrategyTest {

    private MultiTenancyProperties props;
    private TenantInfoProvider provider;

    @BeforeEach
    void setUp() {
        props = new MultiTenancyProperties();
        provider = new TenantInfoProvider() {
            @Override public TenantInfo getTenantInfo(Long tenantId) { return null; }
            @Override public boolean exists(Long tenantId) { return false; }
        };
        TenantContext.init(new ThreadLocalHolder());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        DataSourceContextHolder.clear();
        TableSuffixHolder.clear();
    }

    private TenantInfo tenantInfo(IsolationMode mode) {
        return new TenantInfo()
                .setTenantId(1001L)
                .setMode(mode)
                .setDataSourceName("ds_1001")
                .setSchemaName("tenant_1001")
                .setTableSuffix("_1001")
                .setStatus(TenantStatus.ACTIVE);
    }

    @Nested
    @DisplayName("ColumnStrategy")
    class Column {
        ColumnStrategy strategy = new ColumnStrategy(props, provider);

        @Test void supportsColumn() { assertThat(strategy.supports(IsolationMode.COLUMN)).isTrue(); }
        @Test void notSupportsTable() { assertThat(strategy.supports(IsolationMode.TABLE)).isFalse(); }

        @Test void beforeSqlExecuteWithValidContext() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                strategy.beforeSqlExecute(null, tenantInfo(IsolationMode.COLUMN));
                // no exception = pass
            });
        }

        @Test void beforeSqlExecuteWithoutContextThrows() {
            assertThatThrownBy(() -> strategy.beforeSqlExecute(null, tenantInfo(IsolationMode.COLUMN)))
                    .isInstanceOf(BusinessException.class);
        }
    }

    @Nested
    @DisplayName("TableStrategy")
    class Table {
        TableStrategy strategy = new TableStrategy(props, provider);

        @Test void supportsTable() { assertThat(strategy.supports(IsolationMode.TABLE)).isTrue(); }
        @Test void notSupportsColumn() { assertThat(strategy.supports(IsolationMode.COLUMN)).isFalse(); }

        @Test void beforeSqlExecuteSetsTableSuffix() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                strategy.beforeSqlExecute(null, tenantInfo(IsolationMode.TABLE));
                assertThat(TableSuffixHolder.get()).isEqualTo("_1001");
            });
        }

        @Test void rejectsNullTableSuffix() {
            TenantInfo info = tenantInfo(IsolationMode.TABLE).setTableSuffix(null);
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
            });
        }

        @Test void rejectsEmptyTableSuffix() {
            TenantInfo info = tenantInfo(IsolationMode.TABLE).setTableSuffix("");
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
            });
        }

        @Test void rejectsSqlInjectionTableSuffix() {
            TenantInfo info = tenantInfo(IsolationMode.TABLE).setTableSuffix("_1001; DROP TABLE users;--");
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
            });
        }

        @Test void rejectsTableSuffixWithSpecialChars() {
            TenantInfo info = tenantInfo(IsolationMode.TABLE).setTableSuffix("_1001' OR '1'='1");
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
            });
        }

        @Test void acceptsValidAlphanumericSuffix() {
            TenantInfo info = tenantInfo(IsolationMode.TABLE).setTableSuffix("abc_123_XYZ");
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                strategy.beforeSqlExecute(null, info);
                assertThat(TableSuffixHolder.get()).isEqualTo("abc_123_XYZ");
            });
        }
    }

    @Nested
    @DisplayName("DatabaseStrategy")
    class Database {
        DatabaseStrategy strategy = new DatabaseStrategy(props, provider);

        @Test void supportsDatabase() { assertThat(strategy.supports(IsolationMode.DATABASE)).isTrue(); }
        @Test void notSupportsSchema() { assertThat(strategy.supports(IsolationMode.SCHEMA)).isFalse(); }

        @Test void beforeSqlExecuteSetsDataSourceKey() {
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                strategy.beforeSqlExecute(null, tenantInfo(IsolationMode.DATABASE));
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds_1001");
            });
        }

        @Test void beforeSqlExecuteWithNullDataSourceThrows() {
            TenantInfo info = tenantInfo(IsolationMode.DATABASE).setDataSourceName(null);
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(IllegalArgumentException.class);
            });
        }
    }

    @Nested
    @DisplayName("SchemaStrategy")
    class Schema {
        SchemaStrategy strategy = new SchemaStrategy(props, provider);

        @Test void supportsSchema() { assertThat(strategy.supports(IsolationMode.SCHEMA)).isTrue(); }
        @Test void notSupportsDatabase() { assertThat(strategy.supports(IsolationMode.DATABASE)).isFalse(); }

@Test void invalidSchemaNameThrows() {
            TenantInfo info = tenantInfo(IsolationMode.SCHEMA).setSchemaName("bad;schema");
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .extracting("code")
                        .isEqualTo(TenantErrorCode.TENANT_INVALID_NAMING.name());
            });
        }

        @Test void nullSchemaNameThrows() {
            TenantInfo info = tenantInfo(IsolationMode.SCHEMA).setSchemaName(null);
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class);
            });
        }
    }

    @Nested
    @DisplayName("HybridStrategy")
    class Hybrid {

        private HybridStrategy newHybridStrategy() {
            return new HybridStrategy(props, provider,
                    new ColumnStrategy(props, provider),
                    new TableStrategy(props, provider),
                    new SchemaStrategy(props, provider),
                    new DatabaseStrategy(props, provider));
        }

        @Test void supportsHybrid() {
            assertThat(newHybridStrategy().supports(IsolationMode.HYBRID)).isTrue();
        }

        @Test void delegatesToColumnStrategy() {
            HybridStrategy strategy = newHybridStrategy();
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                TenantInfo info = tenantInfo(IsolationMode.COLUMN);
                strategy.beforeSqlExecute(null, info);
                // ColumnStrategy 只校验，不设置上下文
            });
        }

        @Test void delegatesToDatabaseStrategy() {
            HybridStrategy strategy = newHybridStrategy();
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                TenantInfo info = tenantInfo(IsolationMode.DATABASE);
                strategy.beforeSqlExecute(null, info);
                assertThat(DataSourceContextHolder.get()).isEqualTo("ds_1001");
            });
        }

        @Test void delegatesToTableStrategy() {
            HybridStrategy strategy = newHybridStrategy();
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                TenantInfo info = tenantInfo(IsolationMode.TABLE);
                strategy.beforeSqlExecute(null, info);
                assertThat(TableSuffixHolder.get()).isEqualTo("_1001");
            });
        }

        @Test void rejectsHybridTargetMode() {
            HybridStrategy strategy = newHybridStrategy();
            TenantContext.runWithTenant(new TenantPrincipal().setTenantId(1001L), () -> {
                TenantInfo info = tenantInfo(IsolationMode.HYBRID);
                assertThatThrownBy(() -> strategy.beforeSqlExecute(null, info))
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("cannot delegate to itself");
            });
        }
    }
}
