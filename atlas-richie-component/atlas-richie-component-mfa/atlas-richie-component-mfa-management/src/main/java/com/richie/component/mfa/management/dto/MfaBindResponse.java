package com.richie.component.mfa.management.dto;

import lombok.Data;

import java.util.List;

/**
 * MFA绑定响应DTO
 * <p>
 * 用于返回 MFA 设备绑定的结果信息
 * <p>
 * API路径：POST /api/mfa/bind
 *
 * @author richie696
 * @since 1.0.0
 */
@Data
public class MfaBindResponse {

    /**
     * 二维码URL
     * <p>
     * 用于前端显示二维码图片，用户扫描后可在 MFA 应用中添加账户
     */
    private String qrCodeUrl;

    /**
     * 密钥（Base32编码，仅返回一次）
     * <p>
     * 用于手动输入场景，如果用户无法扫描二维码，可以手动输入此密钥
     * <p>
     * 注意：此字段仅在绑定接口返回一次，后续无法再次获取
     */
    private String secretKey;

    /**
     * 备份码列表（仅返回一次）
     * <p>
     * 用于紧急情况，当用户无法使用 MFA 设备时，可以使用备份码进行验证
     * <p>
     * 注意：此字段仅在绑定接口返回一次，后续无法再次获取
     */
    private List<String> backupCodes;

    /**
     * 二维码过期时间（秒）
     * <p>
     * 二维码的有效期，超过此时间后需要重新绑定
     * <p>
     * 默认值：600秒（10分钟）
     */
    private Integer expiresIn;
}
