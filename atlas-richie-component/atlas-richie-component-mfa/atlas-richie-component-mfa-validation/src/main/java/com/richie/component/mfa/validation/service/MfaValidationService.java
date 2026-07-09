/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.validation.service;

import com.richie.component.mfa.validation.dto.MfaValidationResult;

/**
 * MFA 验证服务接口。
 * <p>
 * 职责：提供 MFA 验证相关的核心业务能力，供网关层调用。
 * <p>
 * 设计原则：
 * <ul>
 *   <li>只读 GlobalCache，零数据库依赖</li>
 *   <li>不处理 HTTP 请求/响应，只返回业务结果</li>
 *   <li>网关层负责根据结果决定如何响应前端</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
public interface MfaValidationService {

    /**
     * 检查用户 MFA 状态。
     * <p>
     * 用于登录成功后，判断是否需要 MFA 验证。
     * <p>
     * 检查逻辑：
     * <ol>
     *   <li>从 GlobalCache 读取用户 MFA 信息</li>
     *   <li>检查是否已绑定 MFA 设备</li>
     *   <li>检查是否已启用 MFA</li>
     *   <li>如果提供了 deviceId，检查是否为可信设备（且未过期）</li>
     *   <li>检查账户是否已锁定</li>
     * </ol>
     *
     * @param userId 用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param deviceId 设备ID（可选，用于检查可信设备）
     * @return MFA 验证结果
     * <ul>
     *   <li>如果 {@code mfaRequired = false}：用户未启用 MFA 或已通过可信设备，可直接登录</li>
     *   <li>如果 {@code mfaRequired = true}：需要进行 MFA 验证，前端应弹出验证码输入界面</li>
     * </ul>
     */
    MfaValidationResult checkMfaStatus(String userId, String tenantId, String deviceId);

    /**
     * 验证 MFA 验证码。
     * <p>
     * 验证逻辑：
     * <ol>
     *   <li>从 GlobalCache 读取用户 MFA 信息</li>
     *   <li>检查 MFA 状态（是否启用、是否锁定）</li>
     *   <li>验证 TOTP 验证码（使用 TotpValidationEngine）</li>
     *   <li>防重放检查（使用 ReplayAttackPreventionService）</li>
     *   <li>记录失败次数（如果验证失败）</li>
     *   <li>标记验证码已使用（如果验证成功）</li>
     * </ol>
     *
     * @param userId 用户ID（必填）
     * @param tenantId 租户ID（可选）
     * @param mfaCode TOTP 验证码（6位数字）
     * @return MFA 验证结果
     * <ul>
     *   <li>如果 {@code success = true}：验证通过，网关可签发正式访问 Token</li>
     *   <li>如果 {@code success = false}：验证失败，返回 {@code errorCode} 和 {@code errorMessage}</li>
     * </ul>
     */
    MfaValidationResult verifyMfaCode(String userId, String tenantId, String mfaCode);

    /**
     * 检查设备是否为可信设备。
     * <p>
     * 用于在登录时判断是否可以跳过 MFA 验证。
     *
     * @param userId 用户ID（必填）
     * @param tenantId 租户ID（可选）
     * @param deviceId 设备ID（必填）
     * @return MFA 验证结果
     * <ul>
     *   <li>如果 {@code trustedDevice = true} 且 {@code trustedDeviceExpired = false}：设备可信，可跳过 MFA</li>
     *   <li>如果 {@code trustedDevice = false} 或 {@code trustedDeviceExpired = true}：需要 MFA 验证</li>
     * </ul>
     */
    MfaValidationResult checkTrustedDevice(String userId, String tenantId, String deviceId);
}
