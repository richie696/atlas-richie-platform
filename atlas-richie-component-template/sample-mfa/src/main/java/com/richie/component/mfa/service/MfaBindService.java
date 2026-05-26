package com.richie.component.mfa.service;

import com.richie.component.mfa.management.dto.MfaActivateRequest;
import com.richie.component.mfa.management.dto.MfaBindRequest;
import com.richie.component.mfa.management.dto.MfaBindResponse;
import com.richie.component.mfa.management.dto.MfaStatusResponse;
import com.richie.component.mfa.management.dto.MfaUnbindRequest;

/**
 * MFA 绑定服务（委托 richie-component-mfa-management 能力）
 * <p>
 * 流程：发起绑定 → 返回二维码 URL → 用户扫码 → 输入验证码 → 激活 → 绑定成功
 */
public interface MfaBindService {

    /**
     * 发起 MFA 绑定，生成二维码等信息供前端展示
     * <p>
     * 前端将 qrCodeUrl 转成二维码图片，用户用 Authenticator 等 App 扫码后，在 App 中会显示 6 位验证码，
     * 用户再在网页输入验证码后调用 {@link #activate(MfaActivateRequest)} 完成激活即绑定成功。
     *
     * @param request 绑定请求（userId 必填，tenantId 可选，deviceType 默认 TOTP）
     * @return 绑定结果（qrCodeUrl、secretKey、backupCodes、expiresIn）
     */
    MfaBindResponse bind(MfaBindRequest request);

    /**
     * 激活 MFA：校验用户输入的 TOTP 验证码，通过则标记为已启用
     *
     * @param request 激活请求（userId、code 必填，其余可选）
     * @return 是否激活成功
     */
    boolean activate(MfaActivateRequest request);

    /**
     * 查询当前用户的 MFA 状态（是否已绑定、是否已激活等）
     *
     * @param userId   用户 ID（与 user_info.id 对应）
     * @param tenantId 租户 ID（可选，本示例可为 null）
     * @return MFA 状态
     */
    MfaStatusResponse getStatus(String userId, String tenantId);

    /**
     * 解绑 MFA
     *
     * @param request 解绑请求（userId 必填）
     * @return 是否解绑成功
     */
    boolean unbind(MfaUnbindRequest request);
}
