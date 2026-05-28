package com.richie.component.mfa.management.dto;

import lombok.Data;

/**
 * 设为主管理设备请求
 * <p>
 * 仅当前主设备可将另一台设备设为主设备；主设备可移除其他可信设备，非主设备仅可查看。
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class SetPrimaryTrustedDeviceRequest {

    /**
     * 用户ID（必填）
     */
    private String userId;

    /**
     * 当前请求设备ID（须为主管理设备，必填）
     */
    private String currentDeviceId;

    /**
     * 租户ID（可选，未启用租户时为 null）
     */
    private String tenantId;
}
