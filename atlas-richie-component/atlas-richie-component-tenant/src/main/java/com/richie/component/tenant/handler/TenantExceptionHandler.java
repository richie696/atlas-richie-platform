package com.richie.component.tenant.handler;

import com.richie.contract.exception.BusinessException;
import com.richie.component.tenant.exception.DataSourceUnavailableException;
import com.richie.component.tenant.exception.TenantMigratingException;
import com.richie.component.tenant.exception.TenantModeMigrationException;
import com.richie.component.tenant.exception.TenantNotFoundException;
import com.richie.component.tenant.exception.TenantProvisionException;
import com.richie.component.tenant.exception.TenantSwitchInTransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 多租户全局异常处理器。
 *
 * <p>统一捕获租户模块抛出的 7 种异常，转换为标准 JSON 错误响应。
 * 响应格式：{@code {code, msg, timestamp, data: null}}。</p>
 *
 * @author richie696
 * @since 2.0
 */
@RestControllerAdvice
public class TenantExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantExceptionHandler.class);

    @ExceptionHandler(DataSourceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleDataSourceUnavailable(
        DataSourceUnavailableException e) {
        log.warn("Data source unavailable: {}", e.getMessage());
        return buildResponse(503, "TENANT_DATA_SOURCE_UNAVAILABLE", e.getMessage());
    }

    @ExceptionHandler(TenantMigratingException.class)
    public ResponseEntity<Map<String, Object>> handleTenantMigrating(
        TenantMigratingException e) {
        log.warn("Tenant migrating: {}", e.getMessage());
        return buildResponse(503, "TENANT_MIGRATING", e.getMessage());
    }

    @ExceptionHandler(TenantSwitchInTransactionException.class)
    public ResponseEntity<Map<String, Object>> handleTenantSwitchInTx(
        TenantSwitchInTransactionException e) {
        log.warn("Tenant switch in transaction: from={} to={}", e.getFromTenantId(), e.getToTenantId());
        return buildResponse(403, "TENANT_SWITCH_IN_TRANSACTION", e.getMessage());
    }

    @ExceptionHandler(TenantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleTenantNotFound(
        TenantNotFoundException e) {
        log.warn("Tenant not found: {}", e.getMessage());
        return buildResponse(404, "TENANT_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(TenantModeMigrationException.class)
    public ResponseEntity<Map<String, Object>> handleModeMigration(
        TenantModeMigrationException e) {
        log.warn("Mode migration denied: {}", e.getMessage());
        return buildResponse(403, "TENANT_MODE_MIGRATION_DENIED", e.getMessage());
    }

    @ExceptionHandler(TenantProvisionException.class)
    public ResponseEntity<Map<String, Object>> handleProvision(
        TenantProvisionException e) {
        log.error("Tenant provision failed: {}", e.getMessage());
        return buildResponse(500, "TENANT_PROVISION_FAILED", e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(
        BusinessException e) {
        log.warn("Tenant business error: {}", e.getMessage());
        return buildResponse(403, e.getCode(), e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(int httpStatus, String code, String msg) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", msg);
        body.put("timestamp", System.currentTimeMillis());
        body.put("data", null);
        return ResponseEntity.status(httpStatus).body(body);
    }
}
