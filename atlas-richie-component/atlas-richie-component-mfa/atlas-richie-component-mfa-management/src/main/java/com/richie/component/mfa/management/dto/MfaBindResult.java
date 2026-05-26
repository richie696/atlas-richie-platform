package com.richie.component.mfa.management.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MFA绑定结果（内部DTO）
 * <p>
 * 用于Service层返回绑定结果，包含二维码、密钥、备份码等信息。
 * <p>
 * 注意：此DTO包含敏感信息（密钥、备份码），仅在Service层使用，不直接返回给前端。
 * 前端应使用 {@link MfaBindResponse} 作为响应对象。
 *
 * @author richie696
 * @since 5.0.0
 */
@Data
@Builder
public class MfaBindResult {

    /**
     * 二维码URL（otpauth://格式）
     * <p>
     * 用于生成二维码图片，用户扫描后可在 MFA 应用中添加账户
     */
    private String qrCodeUrl;

    /**
     * 密钥（明文，Base32编码，仅返回一次）
     * <p>
     * 用于手动输入场景，如果用户无法扫描二维码，可以手动输入此密钥
     * <p>
     * 注意：此字段仅在绑定接口返回一次，后续无法再次获取
     */
    private String secretKey;

    /**
     * 备份码列表（明文，仅返回一次）
     * <p>
     * 用于紧急情况，当用户无法使用 MFA 设备时，可以使用备份码进行验证
     * <p>
     * 注意：此字段仅在绑定接口返回一次，后续无法再次获取
     */
    private List<String> backupCodes;

    /**
     * 过期时间（秒）
     * <p>
     * 二维码的有效期，超过此时间后需要重新绑定
     * <p>
     * 默认值：600秒（10分钟）
     */
    private Integer expiresIn;
}
