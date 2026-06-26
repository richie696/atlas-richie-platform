package com.richie.component.tenant.config;

import com.richie.component.tenant.model.IsolationMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MultiTenancyProperties — 多租户统一配置模型")
class MultiTenancyPropertiesTest {

    @Nested
    @DisplayName("默认值")
    class Defaults {

        @Test
        @DisplayName("enabled 默认 true")
        void enabledDefaultTrue() {
            assertThat(new MultiTenancyProperties().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("mode 默认 COLUMN")
        void modeDefaultColumn() {
            assertThat(new MultiTenancyProperties().getMode()).isEqualTo(IsolationMode.COLUMN);
        }

        @Test
        @DisplayName("tenantIdHeader 默认 X-Tenant-ID")
        void tenantIdHeaderDefault() {
            assertThat(new MultiTenancyProperties().getTenantIdHeader()).isEqualTo("X-Tenant-ID");
        }

        @Test
        @DisplayName("enforceAuthTenant 默认 true")
        void enforceAuthTenantDefaultTrue() {
            assertThat(new MultiTenancyProperties().isEnforceAuthTenant()).isTrue();
        }

        @Test
        @DisplayName("tenantIdColumn 默认 tenant_id")
        void tenantIdColumnDefault() {
            assertThat(new MultiTenancyProperties().getTenantIdColumn()).isEqualTo("tenant_id");
        }

        @Test
        @DisplayName("ignoreTables 默认空列表")
        void ignoreTablesDefaultEmpty() {
            assertThat(new MultiTenancyProperties().getIgnoreTables()).isEmpty();
        }

        @Test
        @DisplayName("tableNameSuffix 默认 _${tenant}")
        void tableNameSuffixDefault() {
            assertThat(new MultiTenancyProperties().getTableNameSuffix()).isEqualTo("_${tenant}");
        }

        @Test
        @DisplayName("schemaPrefix 默认 tenant_")
        void schemaPrefixDefault() {
            assertThat(new MultiTenancyProperties().getSchemaPrefix()).isEqualTo("tenant_");
        }

        @Test
        @DisplayName("schemaAutoCreate 默认 false")
        void schemaAutoCreateDefaultFalse() {
            assertThat(new MultiTenancyProperties().isSchemaAutoCreate()).isFalse();
        }

        @Test
        @DisplayName("forceThreadLocal 默认 false")
        void forceThreadLocalDefaultFalse() {
            assertThat(new MultiTenancyProperties().isForceThreadLocal()).isFalse();
        }

        @Test
        @DisplayName("microservice 默认 true")
        void microserviceDefaultTrue() {
            assertThat(new MultiTenancyProperties().isMicroservice()).isTrue();
        }
    }

    @Nested
    @DisplayName("嵌套配置类")
    class NestedConfig {

        @Test
        @DisplayName("DataSourceConfig 默认非 null")
        void datasourceNotNull() {
            assertThat(new MultiTenancyProperties().getDatasource()).isNotNull();
        }

        @Test
        @DisplayName("SharedDataSourceConfig HikariConfig 默认 poolSize=0")
        void sharedHikariDefaults() {
            MultiTenancyProperties.HikariConfig hikari =
                    new MultiTenancyProperties().getDatasource().getShared().getHikari();
            assertThat(hikari.getMaximumPoolSize()).isZero();
            assertThat(hikari.getMinimumIdle()).isZero();
            assertThat(hikari.getIdleTimeout()).isZero();
            assertThat(hikari.getConnectionTimeout()).isZero();
        }

        @Test
        @DisplayName("CircuitBreakerConfig 默认 failureThreshold=5, openWindowMs=30000")
        void circuitBreakerDefaults() {
            MultiTenancyProperties.CircuitBreakerConfig circuit =
                    new MultiTenancyProperties().getCircuit();
            assertThat(circuit.getFailureThreshold()).isEqualTo(5);
            assertThat(circuit.getOpenWindowMs()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("HealthProbeConfig 默认 probeIntervalMs=30000")
        void healthProbeDefaults() {
            assertThat(new MultiTenancyProperties().getHealth().getProbeIntervalMs()).isEqualTo(30_000L);
        }

        @Test
        @DisplayName("CanaryConfig 默认空列表")
        void canaryDefaultsEmpty() {
            assertThat(new MultiTenancyProperties().getCanary().getTenants()).isEmpty();
        }

        @Test
        @DisplayName("CanaryTenant 默认 ratio=100")
        void canaryTenantDefaultRatio() {
            MultiTenancyProperties.CanaryTenant ct = new MultiTenancyProperties.CanaryTenant();
            assertThat(ct.getRatio()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("设置 ignoreTables 后生效")
        void setIgnoreTables() {
            MultiTenancyProperties props = new MultiTenancyProperties();
            props.setIgnoreTables(List.of("dict_table", "config_table"));
            assertThat(props.getIgnoreTables()).containsExactly("dict_table", "config_table");
        }

        @Test
        @DisplayName("设置 mode 为 DATABASE 后生效")
        void setModeDatabase() {
            MultiTenancyProperties props = new MultiTenancyProperties();
            props.setMode(IsolationMode.DATABASE);
            assertThat(props.getMode()).isEqualTo(IsolationMode.DATABASE);
        }
    }
}
