package com.richie.gateway.dto;

import lombok.Data;

/**
 * 备份码验证请求 DTO
 * <p>
 * 字段设计与 {@code com.richie.component.mfa.management.dto.MfaBackupCodeVerifyRequest}
 * 保持同名同结构，便于通过 JSON 直接映射。
 *
 * @author richie-platform
 * @since 5.0.0
 */
@Data
public class MfaBackupCodeVerifyRequest {

    private String userId;
    private String tenantId;
    private String code;
}
