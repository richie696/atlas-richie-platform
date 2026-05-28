package com.richie.component.mfa.management.controller;

import com.richie.component.mfa.management.dto.*;
import com.richie.contract.model.ApiResult;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.entity.MfaTrustedDevice;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.management.manager.MfaBindManager;
import com.richie.component.mfa.management.manager.MfaStatusManager;
import com.richie.component.mfa.management.manager.TrustedDeviceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.List;

/**
 * MFA管理Controller
 * <p>
 * 部署位置：richie-general-service
 * 职责：提供MFA管理相关的RESTful API
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("${platform.component.mfa.management.api-prefix:/mfa}")
@ConditionalOnProperty(
        prefix = "platform.component.mfa.management",
        name = "controller-enabled",
        havingValue = "true",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class MfaManagementController {

    /**
     * MFA绑定管理器（处理绑定、激活、解绑等操作）
     */
    private final MfaBindManager bindManager;

    /**
     * MFA状态管理器（查询MFA状态）
     */
    private final MfaStatusManager statusManager;

    /**
     * 可信设备管理器（管理可信设备的注册、查询、撤销等操作）
     */
    private final TrustedDeviceManager trustedDeviceManager;

    /**
     * MFA统一配置属性
     */
    private final MfaProperties properties;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * 绑定MFA设备
     * <p>
     * 为指定用户生成 MFA 密钥、二维码和备份码，并保存到数据库（状态为未激活）
     * <p>
     * API路径：POST /api/mfa/bind
     *
     * @param request 绑定请求
     *                <ul>
     *                  <li>{@code tenantId}：租户ID（可选，如果未启用租户则忽略）</li>
     *                  <li>{@code userId}：用户ID（必填）</li>
     *                  <li>{@code deviceType}：设备类型（例如 "TOTP"）</li>
     *                </ul>
     * @return 绑定结果
     * <ul>
     *   <li>{@code qrCodeUrl}：二维码图片URL（用于前端显示）</li>
     *   <li>{@code secretKey}：密钥（Base32编码，用于手动输入）</li>
     *   <li>{@code backupCodes}：备份码列表（用于紧急情况）</li>
     *   <li>{@code expiresIn}：二维码有效期（秒）</li>
     * </ul>
     */
    @PostMapping("/bind")
    public ApiResult<MfaBindResponse> bind(@RequestBody MfaBindRequest request) {
        // 如果未启用租户，tenantId可以为null或空字符串
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;

        MfaBindResult result = bindManager.bindDevice(
                tenantId,
                request.getUserId(),
                request.getDeviceType()
        );

        MfaBindResponse response = new MfaBindResponse();
        response.setQrCodeUrl(result.getQrCodeUrl());
        response.setSecretKey(result.getSecretKey());
        response.setBackupCodes(result.getBackupCodes());
        response.setExpiresIn(result.getExpiresIn());

        return ApiResult.success(response);
    }

    /**
     * 激活MFA设备
     * <p>
     * 验证用户输入的验证码，如果正确则激活 MFA 设备（状态改为已启用）
     * <p>
     * API路径：POST /api/mfa/activate
     *
     * @param request 激活请求
     *                <ul>
     *                  <li>{@code tenantId}：租户ID（可选，如果未启用租户则忽略）</li>
     *                  <li>{@code userId}：用户ID（必填）</li>
     *                  <li>{@code code}：TOTP验证码（6位数字，必填）</li>
     *                  <li>{@code deviceId}：设备ID（可选，用于注册可信设备）</li>
     *                  <li>{@code deviceName}：设备名称（可选，用于显示）</li>
     *                  <li>{@code deviceFingerprint}：设备指纹（可选，用于审计）</li>
     *                  <li>{@code trustDevice}：是否信任此设备（可选，true表示注册为可信设备）</li>
     *                </ul>
     * @return 激活结果
     * <ul>
     *   <li>成功：{@code code = "SUCCESS"}</li>
     *   <li>失败：{@code code = "ERROR", msg = "激活失败，验证码错误"}</li>
     * </ul>
     */
    @PostMapping("/activate")
    public ApiResult<Void> activate(@RequestBody MfaActivateRequest request) {
        // 如果未启用租户，tenantId可以为null或空字符串
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;

        boolean success = bindManager.activateDevice(
                tenantId,
                request.getUserId(),
                request.getCode(),
                request.getDeviceId(),
                request.getDeviceName(),
                request.getDeviceFingerprint(),
                request.getTrustDevice()
        );

        if (success) {
            return ApiResult.success();
        } else {
            return ApiResult.error("激活失败，验证码错误");
        }
    }

    /**
     * 解绑MFA设备
     * <p>
     * 删除用户的 MFA 配置（逻辑删除），并清除缓存
     * <p>
     * API路径：POST /api/mfa/unbind
     *
     * @param request 解绑请求
     *                <ul>
     *                  <li>{@code tenantId}：租户ID（可选，如果未启用租户则忽略）</li>
     *                  <li>{@code userId}：用户ID（必填）</li>
     *                </ul>
     * @return 解绑结果
     * <ul>
     *   <li>成功：{@code code = "SUCCESS"}</li>
     *   <li>失败：{@code code = "ERROR", msg = "解绑失败，MFA配置不存在"}</li>
     * </ul>
     */
    @PostMapping("/unbind")
    public ApiResult<Void> unbind(@RequestBody MfaUnbindRequest request) {
        // 如果未启用租户，tenantId可以为null或空字符串
        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;

        boolean success = bindManager.unbindDevice(
                tenantId,
                request.getUserId()
        );

        if (success) {
            return ApiResult.success();
        } else {
            return ApiResult.error("解绑失败，MFA配置不存在");
        }
    }

    /**
     * 查询MFA状态
     * <p>
     * 查询指定用户的 MFA 绑定状态、设备类型、可信设备数量等信息
     * <p>
     * API路径：GET /api/mfa/status
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则忽略）
     * @return MFA状态信息
     * <ul>
     *   <li>{@code userId}：用户ID</li>
     *   <li>{@code status}：MFA状态（NOT_BOUND/NOT_ACTIVATED/ENABLED/DISABLED）</li>
     *   <li>{@code deviceType}：设备类型（如果已绑定）</li>
     *   <li>{@code bindTime}：绑定时间（如果已绑定）</li>
     *   <li>{@code lastUsedTime}：最后使用时间（如果已使用）</li>
     *   <li>{@code trustedDevices}：可信设备数量</li>
     *   <li>{@code backupCodesRemaining}：剩余备份码数量</li>
     * </ul>
     */
    @GetMapping("/status")
    public ApiResult<MfaStatusResponse> getStatus(
            @RequestParam String userId,
            @RequestParam(required = false) String tenantId) {
        // 如果未启用租户，tenantId可以为null
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        MfaStatusResponse response = statusManager.getStatus(actualTenantId, userId);
        return ApiResult.success(response);
    }

    /**
     * 注册可信设备
     * <p>
     * 在用户完成 MFA 验证后，将当前设备注册为可信设备，以便后续登录可以跳过 MFA 验证。
     * <p>
     * API路径：POST /api/mfa/trusted-devices
     *
     * @param request             注册请求（body）
     * @param hardwareFingerprint 设备指纹，从请求头 X-Hardware-Fingerprint 注入；body 未传时使用此值写入数据库
     * @return 注册成功的可信设备信息
     * <ul>
     *   <li>{@code deviceId}：设备ID</li>
     *   <li>{@code deviceName}：设备名称</li>
     *   <li>{@code trustedUntil}：信任失效时间</li>
     *   <li>{@code lastUsedTime}：最后使用时间</li>
     *   <li>{@code createdAt}：创建时间</li>
     * </ul>
     */
    @PostMapping("/trusted-devices")
    public ApiResult<TrustedDeviceVO> registerTrustedDevice(
            @RequestBody TrustedDeviceRegisterRequest request,
            @RequestHeader(value = "X-Hardware-Fingerprint", required = false) String hardwareFingerprint) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;
        // 设备指纹：优先用 body，未传则用请求头 X-Hardware-Fingerprint（前端框架会自动附带）
        String deviceFingerprint = StringUtils.isNotBlank(request.getDeviceFingerprint())
                ? request.getDeviceFingerprint()
                : hardwareFingerprint;

        MfaTrustedDevice device = trustedDeviceManager.registerTrustedDevice(
                actualTenantId,
                request.getUserId(),
                request.getDeviceId(),
                request.getDeviceName(),
                deviceFingerprint
        );

        TrustedDeviceVO vo = TrustedDeviceVO.builder()
                .deviceId(device.getDeviceId())
                .deviceName(device.getDeviceName())
                .trustedUntil(device.getTrustedUntil())
                .lastUsedTime(device.getLastUsedTime())
                .createdAt(device.getCreateTime().atOffset(ZoneOffset.UTC))
                .isPrimary(device.getIsPrimary() != null && device.getIsPrimary() == 1)
                .build();

        return ApiResult.success(vo);
    }

    /**
     * 查询可信设备列表
     * <p>
     * 查询指定用户的所有可信设备列表
     * <p>
     * API路径：GET /api/mfa/trusted-devices
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则忽略）
     * @return 可信设备列表
     * <ul>
     *   <li>{@code devices}：可信设备列表（包含设备ID、设备名称、信任过期时间、最后使用时间等）</li>
     *   <li>{@code total}：设备总数</li>
     *   <li>{@code maxDevices}：最大允许的设备数量</li>
     * </ul>
     */
    @GetMapping("/trusted-devices")
    public ApiResult<TrustedDeviceListResponse> listTrustedDevices(
            @RequestParam String userId,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String currentDeviceId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;

        List<MfaTrustedDevice> devices = trustedDeviceManager.listTrustedDevices(actualTenantId, userId);
        MfaTrustedDevice primary = trustedDeviceManager.getPrimaryDevice(actualTenantId, userId);
        boolean currentDeviceIsPrimary = primary != null && StringUtils.isNotBlank(currentDeviceId)
                && currentDeviceId.equals(primary.getDeviceId());

        List<TrustedDeviceVO> deviceVOList = devices.stream()
                .map(device -> TrustedDeviceVO.builder()
                        .deviceId(device.getDeviceId())
                        .deviceName(device.getDeviceName())
                        .trustedUntil(device.getTrustedUntil())
                        .lastUsedTime(device.getLastUsedTime())
                        .createdAt(device.getCreateTime().atOffset(ZoneOffset.UTC))
                        .isPrimary(device.getIsPrimary() != null && device.getIsPrimary() == 1)
                        .build())
                .toList();

        TrustedDeviceListResponse response = TrustedDeviceListResponse.builder()
                .devices(deviceVOList)
                .total(deviceVOList.size())
                .maxDevices(properties.getSecurity().getTrustedDevice().getMaxDevices())
                .currentDeviceIsPrimary(currentDeviceIsPrimary)
                .build();

        return ApiResult.success(response);
    }

    /**
     * 撤销可信设备
     * <p>
     * 删除指定用户的指定可信设备（逻辑删除），并清除缓存
     * <p>
     * API路径：DELETE /api/mfa/trusted-devices/{deviceId}
     *
     * @param deviceId 设备ID（路径参数，必填）
     * @param userId   用户ID（查询参数，必填）
     * @param tenantId 租户ID（查询参数，可选，如果未启用租户则忽略）
     * @return 撤销结果
     * <ul>
     *   <li>成功：{@code code = "SUCCESS"}</li>
     *   <li>失败：{@code code = "ERROR", msg = "可信设备不存在"}</li>
     * </ul>
     */
    @DeleteMapping("/trusted-devices/{deviceId}")
    public ApiResult<Void> revokeTrustedDevice(
            @PathVariable String deviceId,
            @RequestParam String userId,
            @RequestParam String currentDeviceId,
            @RequestParam(required = false) String tenantId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        trustedDeviceManager.revokeTrustedDevice(actualTenantId, userId, deviceId, currentDeviceId);
        return ApiResult.success();
    }

    /**
     * 撤销所有可信设备
     * <p>
     * 删除指定用户的所有可信设备（逻辑删除），并清除缓存
     * <p>
     * API路径：DELETE /api/mfa/trusted-devices/all
     *
     * @param userId   用户ID（查询参数，必填）
     * @param tenantId 租户ID（查询参数，可选，如果未启用租户则忽略）
     * @return 撤销结果
     * <ul>
     *   <li>成功：{@code code = "SUCCESS", data = 撤销的设备数量}</li>
     *   <li>如果没有设备：{@code code = "SUCCESS", data = 0}</li>
     * </ul>
     */
    @DeleteMapping("/trusted-devices/all")
    public ApiResult<Integer> revokeAllTrustedDevices(
            @RequestParam String userId,
            @RequestParam String currentDeviceId,
            @RequestParam(required = false) String tenantId) {
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        int revokedCount = trustedDeviceManager.revokeAllTrustedDevices(actualTenantId, userId, currentDeviceId);
        return ApiResult.success(revokedCount);
    }

    /**
     * 设置主管理设备
     * <p>
     * 仅当前主设备可将另一台设备设为主设备；主设备可移除其他可信设备，非主设备仅可查看。
     * <p>
     * API路径：PUT /api/mfa/trusted-devices/{deviceId}/primary
     * <p>
     * 请求体：{@link SetPrimaryTrustedDeviceRequest}（userId、currentDeviceId 必填；tenantId 可选）
     *
     * @param deviceId 要设为主设备的设备ID（路径参数）
     * @param request  请求体（userId、currentDeviceId 必填）
     * @return 成功：{@code code = "SUCCESS"}
     */
    @PutMapping("/trusted-devices/{deviceId}/primary")
    public ApiResult<Void> setPrimaryTrustedDevice(
            @PathVariable String deviceId,
            @RequestBody SetPrimaryTrustedDeviceRequest request) {
        if (request == null
                || StringUtils.isBlank(request.getUserId())
                || StringUtils.isBlank(request.getCurrentDeviceId())) {
            return ApiResult.error("userId 和 currentDeviceId 不能为空");
        }
        String actualTenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;
        trustedDeviceManager.setPrimaryDevice(actualTenantId, request.getUserId(), deviceId, request.getCurrentDeviceId());
        return ApiResult.success();
    }

    /**
     * 验证并消耗备份码
     * <p>
     * 用户输入 8 位备份码时调用，验证通过则从列表中移除该码（一次性使用）。
     * <p>
     * API路径：POST /api/mfa/backup-codes/verify
     *
     * @param request 备份码验证请求（userId、code 必填；tenantId 可选）
     * @return 验证结果（成功：code = SUCCESS；失败：code = ERROR）
     */
    @PostMapping("/backup-codes/verify")
    public ApiResult<Void> verifyAndConsumeBackupCode(@RequestBody MfaBackupCodeVerifyRequest request) {
        if (request == null || StringUtils.isBlank(request.getUserId())
                || StringUtils.isBlank(request.getCode())) {
            return ApiResult.error("用户ID和备份码不能为空");
        }

        String tenantId = tenantSupport.isTenantEnabled() ? request.getTenantId() : null;
        boolean success = bindManager.verifyAndConsumeBackupCode(
                tenantId,
                request.getUserId(),
                request.getCode()
        );

        if (success) {
            return ApiResult.success();
        }
        return ApiResult.error("备份码错误或已被使用");
    }

}
