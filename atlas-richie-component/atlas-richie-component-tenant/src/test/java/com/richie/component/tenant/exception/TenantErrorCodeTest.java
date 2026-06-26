package com.richie.component.tenant.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantErrorCode — 多租户错误码枚举")
class TenantErrorCodeTest {

    @Test
    @DisplayName("getCode() 返回枚举名")
    void getCodeReturnsEnumName() {
        assertThat(TenantErrorCode.TENANT_AUTH_MISSING_TOKEN.getCode())
                .isEqualTo("TENANT_AUTH_MISSING_TOKEN");
    }

    @Test
    @DisplayName("httpStatus 值正确")
    void httpStatusValues() {
        assertThat(TenantErrorCode.TENANT_AUTH_MISSING_TOKEN.getHttpStatus()).isEqualTo(401);
        assertThat(TenantErrorCode.TENANT_AUTH_BLANK_CLAIM.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_AUTH_INVALID_FORMAT.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_AUTH_EXPIRED.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_AUTH_MISMATCH.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_IDENTITY_NOT_FOUND.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(TenantErrorCode.TENANT_DATA_SOURCE_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
        assertThat(TenantErrorCode.TENANT_SHARED_DS_UNAVAILABLE.getHttpStatus()).isEqualTo(503);
        assertThat(TenantErrorCode.TENANT_SWITCH_IN_TRANSACTION.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_MIGRATING.getHttpStatus()).isEqualTo(503);
        assertThat(TenantErrorCode.TENANT_MODE_MIGRATION_DENIED.getHttpStatus()).isEqualTo(403);
        assertThat(TenantErrorCode.TENANT_PROVISION_FAILED.getHttpStatus()).isEqualTo(500);
        assertThat(TenantErrorCode.TENANT_ADMIN_REQUIRED.getHttpStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("format() 填充占位符 {0}, {1}")
    void formatWithPlaceholders() {
        assertThat(TenantErrorCode.TENANT_AUTH_INVALID_FORMAT.format("abc"))
                .isEqualTo("Invalid tenant ID: abc");
        assertThat(TenantErrorCode.TENANT_AUTH_MISMATCH.format(100L, 200L))
                .isEqualTo("Tenant mismatch: JWT=100 vs Header=200");
        assertThat(TenantErrorCode.TENANT_SWITCH_IN_TRANSACTION.format(100L, 200L))
                .isEqualTo("Cannot switch tenant from 100 to 200 within a transaction");
    }

    @Test
    @DisplayName("format() 无占位符时返回原始消息")
    void formatNoPlaceholders() {
        assertThat(TenantErrorCode.TENANT_AUTH_MISSING_TOKEN.format())
                .isEqualTo("Missing authentication token");
        assertThat(TenantErrorCode.TENANT_SHARED_DS_UNAVAILABLE.format())
                .isEqualTo("Shared data source is currently unavailable");
    }

    @Test
    @DisplayName("所有枚举值的 default message 非空")
    void allDefaultMessagesNotBlank() {
        for (TenantErrorCode code : TenantErrorCode.values()) {
            assertThat(code.getDefaultMessage()).isNotBlank();
        }
    }

    @Test
    @DisplayName("所有枚举值的 httpStatus 在合法范围")
    void allHttpStatusInRange() {
        for (TenantErrorCode code : TenantErrorCode.values()) {
            assertThat(code.getHttpStatus()).isBetween(100, 599);
        }
    }
}
