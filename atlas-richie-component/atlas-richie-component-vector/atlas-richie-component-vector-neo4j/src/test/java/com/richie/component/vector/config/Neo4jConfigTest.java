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
package com.richie.component.vector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Neo4jConfig 配置属性测试.
 *
 * <p>验证所有配置属性的 setter/getter 正常工作，
 * 确保 Spring Boot 的 @ConfigurationProperties 绑定能够正确工作。
 */
class Neo4jConfigTest {

    private final Neo4jConfig config = new Neo4jConfig();

    @Nested
    @DisplayName("基础连接配置")
    class BasicConnectionTests {

        @Test
        @DisplayName("uri 应有默认值")
        void uri_shouldHaveDefault() {
            assertThat(config.getUri()).isEqualTo("bolt://localhost:7687");
        }

        @Test
        @DisplayName("uri 可被覆盖")
        void uri_canBeOverridden() {
            config.setUri("bolt+routing://localhost:7687");
            assertThat(config.getUri()).isEqualTo("bolt+routing://localhost:7687");
        }

        @Test
        @DisplayName("username 可设置")
        void username_canBeSet() {
            config.setUsername("neo4j");
            assertThat(config.getUsername()).isEqualTo("neo4j");
        }

        @Test
        @DisplayName("password 可设置")
        void password_canBeSet() {
            config.setPassword("secret");
            assertThat(config.getPassword()).isEqualTo("secret");
        }

        @Test
        @DisplayName("database 应有默认值")
        void database_shouldHaveDefault() {
            assertThat(config.getDatabase()).isEqualTo("neo4j");
        }

        @Test
        @DisplayName("database 可被覆盖")
        void database_canBeOverridden() {
            config.setDatabase("mydb");
            assertThat(config.getDatabase()).isEqualTo("mydb");
        }
    }

    @Nested
    @DisplayName("连接池配置")
    class ConnectionPoolTests {

        @Test
        @DisplayName("maxConnectionPoolSize 应有默认值 50")
        void maxConnectionPoolSize_shouldDefaultTo50() {
            assertThat(config.getMaxConnectionPoolSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("maxConnectionLifetimeMillis 应有默认值 1小时")
        void maxConnectionLifetimeMillis_shouldDefaultTo1Hour() {
            assertThat(config.getMaxConnectionLifetimeMillis()).isEqualTo(60 * 60 * 1000L);
        }

        @Test
        @DisplayName("connectionIdleTimeoutMillis 应有默认值 30分钟")
        void connectionIdleTimeoutMillis_shouldDefaultTo30Min() {
            assertThat(config.getConnectionIdleTimeoutMillis()).isEqualTo(30 * 60 * 1000L);
        }

        @Test
        @DisplayName("connectionAcquisitionTimeoutMillis 应有默认值 10秒")
        void connectionAcquisitionTimeoutMillis_shouldDefaultTo10Sec() {
            assertThat(config.getConnectionAcquisitionTimeoutMillis()).isEqualTo(10 * 1000L);
        }

        @Test
        @DisplayName("connectionTimeoutMillis 应有默认值 5秒")
        void connectionTimeoutMillis_shouldDefaultTo5Sec() {
            assertThat(config.getConnectionTimeoutMillis()).isEqualTo(5 * 1000L);
        }

        @Test
        @DisplayName("connectionValidationEnabled 应默认为 true")
        void connectionValidationEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionValidationEnabled()).isTrue();
        }

        @Test
        @DisplayName("connectionValidationQuery 应有默认值")
        void connectionValidationQuery_shouldHaveDefault() {
            assertThat(config.getConnectionValidationQuery()).isEqualTo("RETURN 1");
        }
    }

    @Nested
    @DisplayName("负载均衡配置")
    class LoadBalancingTests {

        @Test
        @DisplayName("loadBalancingEnabled 应默认为 false")
        void loadBalancingEnabled_shouldDefaultFalse() {
            assertThat(config.isLoadBalancingEnabled()).isFalse();
        }

        @Test
        @DisplayName("loadBalancingStrategy 应有默认值")
        void loadBalancingStrategy_shouldHaveDefault() {
            assertThat(config.getLoadBalancingStrategy()).isEqualTo("least_connected");
        }

        @Test
        @DisplayName("loadBalancerMaxConnectionPoolSize 应有默认值")
        void loadBalancerMaxConnectionPoolSize_shouldHaveDefault() {
            assertThat(config.getLoadBalancerMaxConnectionPoolSize()).isEqualTo(100);
        }
    }

    @Nested
    @DisplayName("重试和容错配置")
    class RetryFaultToleranceTests {

        @Test
        @DisplayName("retryStrategy 应有默认值")
        void retryStrategy_shouldHaveDefault() {
            assertThat(config.getRetryStrategy()).isEqualTo("exponential_backoff");
        }

        @Test
        @DisplayName("maxRetryAttempts 应有默认值 3")
        void maxRetryAttempts_shouldDefaultTo3() {
            assertThat(config.getMaxRetryAttempts()).isEqualTo(3);
        }

        @Test
        @DisplayName("circuitBreakerEnabled 应默认为 true")
        void circuitBreakerEnabled_shouldDefaultTrue() {
            assertThat(config.isCircuitBreakerEnabled()).isTrue();
        }

        @Test
        @DisplayName("circuitBreakerFailureThreshold 应有默认值")
        void circuitBreakerFailureThreshold_shouldHaveDefault() {
            assertThat(config.getCircuitBreakerFailureThreshold()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("安全配置")
    class SecurityTests {

        @Test
        @DisplayName("encryptionEnabled 应默认为 true")
        void encryptionEnabled_shouldDefaultTrue() {
            assertThat(config.isEncryptionEnabled()).isTrue();
        }

        @Test
        @DisplayName("trustStrategy 应有默认值")
        void trustStrategy_shouldHaveDefault() {
            assertThat(config.getTrustStrategy()).isEqualTo("trust_system_certificates");
        }

        @Test
        @DisplayName("clientCertificateEnabled 应默认为 false")
        void clientCertificateEnabled_shouldDefaultFalse() {
            assertThat(config.isClientCertificateEnabled()).isFalse();
        }

        @Test
        @DisplayName("kerberosEnabled 应默认为 false")
        void kerberosEnabled_shouldDefaultFalse() {
            assertThat(config.isKerberosEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("会话和事务配置")
    class SessionTransactionTests {

        @Test
        @DisplayName("maxTransactionRetryTimeMillis 应有默认值")
        void maxTransactionRetryTimeMillis_shouldHaveDefault() {
            assertThat(config.getMaxTransactionRetryTimeMillis()).isEqualTo(15 * 1000L);
        }

        @Test
        @DisplayName("sessionTimeoutMillis 应有默认值")
        void sessionTimeoutMillis_shouldHaveDefault() {
            assertThat(config.getSessionTimeoutMillis()).isEqualTo(30 * 1000L);
        }

        @Test
        @DisplayName("transactionTimeoutMillis 应有默认值")
        void transactionTimeoutMillis_shouldHaveDefault() {
            assertThat(config.getTransactionTimeoutMillis()).isEqualTo(60 * 1000L);
        }

        @Test
        @DisplayName("autoCommitEnabled 应默认为 false")
        void autoCommitEnabled_shouldDefaultFalse() {
            assertThat(config.isAutoCommitEnabled()).isFalse();
        }

        @Test
        @DisplayName("readWriteSeparationEnabled 应默认为 false")
        void readWriteSeparationEnabled_shouldDefaultFalse() {
            assertThat(config.isReadWriteSeparationEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("查询和性能配置")
    class QueryPerformanceTests {

        @Test
        @DisplayName("queryTimeoutMillis 应有默认值")
        void queryTimeoutMillis_shouldHaveDefault() {
            assertThat(config.getQueryTimeoutMillis()).isEqualTo(30 * 1000L);
        }

        @Test
        @DisplayName("maxQueryResultSize 应有默认值")
        void maxQueryResultSize_shouldHaveDefault() {
            assertThat(config.getMaxQueryResultSize()).isEqualTo(10000);
        }

        @Test
        @DisplayName("queryCacheEnabled 应默认为 true")
        void queryCacheEnabled_shouldDefaultTrue() {
            assertThat(config.isQueryCacheEnabled()).isTrue();
        }

        @Test
        @DisplayName("queryPlanCacheEnabled 应默认为 true")
        void queryPlanCacheEnabled_shouldDefaultTrue() {
            assertThat(config.isQueryPlanCacheEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("监控和日志配置")
    class MonitoringLoggingTests {

        @Test
        @DisplayName("applicationName 应有默认值")
        void applicationName_shouldHaveDefault() {
            assertThat(config.getApplicationName()).isEqualTo("richie-vector-neo4j");
        }

        @Test
        @DisplayName("connectionPoolMonitoringEnabled 应默认为 true")
        void connectionPoolMonitoringEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionPoolMonitoringEnabled()).isTrue();
        }

        @Test
        @DisplayName("queryLoggingEnabled 应默认为 false")
        void queryLoggingEnabled_shouldDefaultFalse() {
            assertThat(config.isQueryLoggingEnabled()).isFalse();
        }

        @Test
        @DisplayName("performanceMonitoringEnabled 应默认为 true")
        void performanceMonitoringEnabled_shouldDefaultTrue() {
            assertThat(config.isPerformanceMonitoringEnabled()).isTrue();
        }

        @Test
        @DisplayName("performanceMonitoringSamplingRate 应有默认值")
        void performanceMonitoringSamplingRate_shouldHaveDefault() {
            assertThat(config.getPerformanceMonitoringSamplingRate()).isEqualTo(0.1);
        }

        @Test
        @DisplayName("connectionLeakDetectionEnabled 应默认为 true")
        void connectionLeakDetectionEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionLeakDetectionEnabled()).isTrue();
        }
    }

    @Nested
    @DisplayName("健康检查配置")
    class HealthCheckTests {

        @Test
        @DisplayName("healthCheckEnabled 应默认为 true")
        void healthCheckEnabled_shouldDefaultTrue() {
            assertThat(config.isHealthCheckEnabled()).isTrue();
        }

        @Test
        @DisplayName("healthCheckQuery 应有默认值")
        void healthCheckQuery_shouldHaveDefault() {
            assertThat(config.getHealthCheckQuery()).isEqualTo("RETURN 1 as health");
        }

        @Test
        @DisplayName("diagnosticEnabled 应默认为 false")
        void diagnosticEnabled_shouldDefaultFalse() {
            assertThat(config.isDiagnosticEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("高级配置")
    class AdvancedConfigTests {

        @Test
        @DisplayName("connectionPoolWarmupEnabled 应默认为 true")
        void connectionPoolWarmupEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionPoolWarmupEnabled()).isTrue();
        }

        @Test
        @DisplayName("connectionPoolWarmupSize 应有默认值")
        void connectionPoolWarmupSize_shouldHaveDefault() {
            assertThat(config.getConnectionPoolWarmupSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("connectionPoolStatsEnabled 应默认为 true")
        void connectionPoolStatsEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionPoolStatsEnabled()).isTrue();
        }

        @Test
        @DisplayName("networkEventListeningEnabled 应默认为 false")
        void networkEventListeningEnabled_shouldDefaultFalse() {
            assertThat(config.isNetworkEventListeningEnabled()).isFalse();
        }

        @Test
        @DisplayName("protocolNegotiationEnabled 应默认为 true")
        void protocolNegotiationEnabled_shouldDefaultTrue() {
            assertThat(config.isProtocolNegotiationEnabled()).isTrue();
        }

        @Test
        @DisplayName("connectionCompressionEnabled 应默认为 true")
        void connectionCompressionEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionCompressionEnabled()).isTrue();
        }

        @Test
        @DisplayName("connectionCompressionLevel 应有默认值")
        void connectionCompressionLevel_shouldHaveDefault() {
            assertThat(config.getConnectionCompressionLevel()).isEqualTo(6);
        }

        @Test
        @DisplayName("connectionReuseEnabled 应默认为 true")
        void connectionReuseEnabled_shouldDefaultTrue() {
            assertThat(config.isConnectionReuseEnabled()).isTrue();
        }
    }
}
