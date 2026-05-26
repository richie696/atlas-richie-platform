package com.richie.component.mfa.management.dto;

import lombok.Data;

/**
 * MFA解绑请求DTO
 * <p>
 * 用于接收前端提交的 MFA 设备解绑请求
 * <p>
 * API路径：POST /api/mfa/unbind
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
public class MfaUnbindRequest {

    /**
     * 用户ID（业务系统User表的主键ID）
     * <p>
     * 必填字段
     */
    private String userId;

    /**
     * 租户ID
     * <p>
     * 可选字段，如果系统未启用租户，此字段可以为 null 或空字符串
     */
    private String tenantId;
}
