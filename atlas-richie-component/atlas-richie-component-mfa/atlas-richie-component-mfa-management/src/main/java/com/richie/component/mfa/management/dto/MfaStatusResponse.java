package com.richie.component.mfa.management.dto;

import com.richie.component.mfa.core.constant.MfaStatusEnum;
import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;

/**
 * MFA状态响应DTO
 * <p>
 * 用于返回用户的 MFA 状态信息
 * <p>
 * API路径：GET /api/mfa/status
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
@Builder
public class MfaStatusResponse {

    /**
     * 用户ID（业务系统User表的主键ID）
     */
    private String userId;

    /**
     * MFA状态
     * <p>
     * 可能的值：
     * <ul>
     *   <li>{@code NOT_BOUND}：未绑定（用户未绑定 MFA 设备）</li>
     *   <li>{@code NOT_ACTIVATED}：未激活（已绑定但未激活）</li>
     *   <li>{@code ENABLED}：已启用（已激活，可以正常使用）</li>
     *   <li>{@code DISABLED}：已禁用（已绑定但被禁用）</li>
     * </ul>
     */
    private MfaStatusEnum status;

    /**
     * 设备类型
     * <p>
     * 如果已绑定，返回设备类型（例如 "TOTP"）
     * <p>
     * 如果未绑定，此字段为 null
     */
    private String deviceType;

    /**
     * 绑定时间
     * <p>
     * 如果已绑定，返回绑定时间
     * <p>
     * 如果未绑定，此字段为 null
     */
    private OffsetDateTime bindTime;

    private OffsetDateTime lastUsedTime;

    /**
     * 可信设备数量
     * <p>
     * 用户已注册的可信设备数量（包括已过期的设备）
     */
    private Integer trustedDevices;

    /**
     * 剩余备份码数量
     * <p>
     * 用户剩余的备份码数量（未被使用的备份码）
     */
    private Integer backupCodesRemaining;
}
