package com.richie.component.tenant.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("异常类构造器覆盖")
class ExceptionTest {

    @Nested
    @DisplayName("BusinessException")
    class BusinessEx {
        @Test void messageOnly() {
            BusinessException e = new BusinessException("err");
            assertThat(e.getMessage()).isEqualTo("err");
            assertThat(e.getCode()).isEqualTo("TENANT_BUSINESS_ERROR");
        }
        @Test void codeAndMessage() {
            BusinessException e = new BusinessException("CUSTOM", "msg");
            assertThat(e.getCode()).isEqualTo("CUSTOM");
            assertThat(e.getMessage()).isEqualTo("msg");
        }
        @Test void messageAndCause() {
            RuntimeException cause = new RuntimeException("root");
            BusinessException e = new BusinessException("err", cause);
            assertThat(e.getCause()).isSameAs(cause);
            assertThat(e.getCode()).isEqualTo("TENANT_BUSINESS_ERROR");
        }
    }

    @Nested
    @DisplayName("DataSourceUnavailableException")
    class DsEx {
        @Test void keyAndMessage() {
            DataSourceUnavailableException e = new DataSourceUnavailableException("ds-1", "down");
            assertThat(e.getDataSourceKey()).isEqualTo("ds-1");
            assertThat(e.getMessage()).isEqualTo("down");
        }
        @Test void messageOnly() {
            DataSourceUnavailableException e = new DataSourceUnavailableException("fail");
            assertThat(e.getDataSourceKey()).isNull();
            assertThat(e.getMessage()).isEqualTo("fail");
        }
    }

    @Nested
    @DisplayName("TenantNotFoundException")
    class NotFoundEx {
        @Test void tenantId() {
            TenantNotFoundException e = new TenantNotFoundException(1001L);
            assertThat(e.getTenantId()).isEqualTo(1001L);
            assertThat(e.getMessage()).contains("1001");
        }
        @Test void messageOnly() {
            TenantNotFoundException e = new TenantNotFoundException("gone");
            assertThat(e.getTenantId()).isNull();
            assertThat(e.getMessage()).isEqualTo("gone");
        }
    }

    @Nested
    @DisplayName("TenantSwitchInTransactionException")
    class SwitchEx {
        @Test void fromAndTo() {
            TenantSwitchInTransactionException e = new TenantSwitchInTransactionException(100L, 200L);
            assertThat(e.getFromTenantId()).isEqualTo(100L);
            assertThat(e.getToTenantId()).isEqualTo(200L);
            assertThat(e.getMessage()).contains("100").contains("200");
        }
    }

    @Nested
    @DisplayName("TenantMigratingException")
    class MigratingEx {
        @Test void tenantId() {
            TenantMigratingException e = new TenantMigratingException(5001L);
            assertThat(e.getTenantId()).isEqualTo(5001L);
            assertThat(e.getMessage()).contains("5001");
        }
    }

    @Nested
    @DisplayName("TenantModeMigrationException")
    class ModeMigrationEx {
        @Test void tenantIdAndMessage() {
            TenantModeMigrationException e = new TenantModeMigrationException(6001L, "denied");
            assertThat(e.getTenantId()).isEqualTo(6001L);
            assertThat(e.getMessage()).isEqualTo("denied");
        }
    }

    @Nested
    @DisplayName("TenantProvisionException")
    class ProvisionEx {
        @Test void tenantIdAndMessage() {
            TenantProvisionException e = new TenantProvisionException(7001L, "failed");
            assertThat(e.getTenantId()).isEqualTo(7001L);
            assertThat(e.getMessage()).isEqualTo("failed");
        }
        @Test void withCause() {
            RuntimeException cause = new RuntimeException("root");
            TenantProvisionException e = new TenantProvisionException(7001L, "fail", cause);
            assertThat(e.getCause()).isSameAs(cause);
        }
    }
}
