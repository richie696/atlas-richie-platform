package com.richie.component.mfa.management.dto;

import lombok.Data;

/**
 * 备份码验证请求 DTO
 * <p>
 * 用于接收用户提交的备份码（8 位数字），验证通过后该备份码将被消耗（一次性使用）。
 * <p>
 * API 路径：POST /api/mfa/backup-codes/verify
 *
 * @author richie-platform
 * @since 1.0.0
 */
@Data
public class MfaBackupCodeVerifyRequest {

    /**
     * 用户 ID（业务系统 User 表的主键 ID）
     * <p>
     * 必填字段
     */
    private String userId;

    /**
     * 租户 ID
     * <p>
     * 可选字段，如果系统未启用租户，此字段可以为 null 或空字符串
     */
    private String tenantId;

    /**
     * 用户输入的备份码（8 位数字，与绑定时的备份码一致）
     * <p>
     * 必填字段，验证通过后该码将被消耗
     */
    private String code;
}
