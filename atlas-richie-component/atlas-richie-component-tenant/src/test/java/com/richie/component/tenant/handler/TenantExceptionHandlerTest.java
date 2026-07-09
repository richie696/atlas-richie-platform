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
package com.richie.component.tenant.handler;

import com.richie.component.tenant.exception.DataSourceUnavailableException;
import com.richie.component.tenant.exception.TenantMigratingException;
import com.richie.component.tenant.exception.TenantModeMigrationException;
import com.richie.component.tenant.exception.TenantNotFoundException;
import com.richie.component.tenant.exception.TenantProvisionException;
import com.richie.component.tenant.exception.TenantSwitchInTransactionException;
import com.richie.contract.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantExceptionHandler — 全局异常处理器")
class TenantExceptionHandlerTest {

    private TenantExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TenantExceptionHandler();
    }

    @Test
    @DisplayName("DataSourceUnavailableException → 503")
    void handleDataSourceUnavailable() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleDataSourceUnavailable(new DataSourceUnavailableException("ds-1", "down"));
        assertThat(resp.getStatusCode().value()).isEqualTo(503);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_DATA_SOURCE_UNAVAILABLE");
    }

    @Test
    @DisplayName("TenantMigratingException → 503")
    void handleTenantMigrating() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleTenantMigrating(new TenantMigratingException(1001L));
        assertThat(resp.getStatusCode().value()).isEqualTo(503);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_MIGRATING");
    }

    @Test
    @DisplayName("TenantSwitchInTransactionException → 403")
    void handleTenantSwitchInTx() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleTenantSwitchInTx(new TenantSwitchInTransactionException(100L, 200L));
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_SWITCH_IN_TRANSACTION");
    }

    @Test
    @DisplayName("TenantNotFoundException → 404")
    void handleTenantNotFound() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleTenantNotFound(new TenantNotFoundException(9999L));
        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_NOT_FOUND");
    }

    @Test
    @DisplayName("TenantModeMigrationException → 403")
    void handleModeMigration() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleModeMigration(new TenantModeMigrationException(1001L, "denied"));
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_MODE_MIGRATION_DENIED");
    }

    @Test
    @DisplayName("TenantProvisionException → 500")
    void handleProvision() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleProvision(new TenantProvisionException(1001L, "failed"));
        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().get("code")).isEqualTo("TENANT_PROVISION_FAILED");
    }

    @Test
    @DisplayName("BusinessException → 403 + 自定义 code")
    void handleBusiness() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleBusiness(new BusinessException("CUSTOM_CODE", "custom msg"));
        assertThat(resp.getStatusCode().value()).isEqualTo(403);
        assertThat(resp.getBody().get("code")).isEqualTo("CUSTOM_CODE");
    }

    @Nested
    @DisplayName("响应格式")
    class ResponseFormat {

        @Test
        @DisplayName("所有响应包含 code, msg, timestamp, data")
        void responseContainsRequiredFields() {
            ResponseEntity<Map<String, Object>> resp =
                    handler.handleTenantNotFound(new TenantNotFoundException(1L));
            Map<String, Object> body = resp.getBody();
            assertThat(body).containsKeys("code", "msg", "timestamp", "data");
            assertThat(body.get("data")).isNull();
            assertThat(body.get("timestamp")).isInstanceOf(Long.class);
        }
    }
}
