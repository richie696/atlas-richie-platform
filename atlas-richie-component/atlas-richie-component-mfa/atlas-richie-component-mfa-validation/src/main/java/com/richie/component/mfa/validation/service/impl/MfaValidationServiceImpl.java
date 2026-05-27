package com.richie.component.mfa.validation.service.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.constant.MfaOperationTypeEnum;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.entity.MfaTrustedDevice;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.event.MfaAuditEvent;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import com.richie.component.mfa.validation.dto.MfaValidationResult;
import com.richie.component.mfa.validation.engine.TotpValidationEngine;
import com.richie.component.mfa.validation.replay.ReplayAttackPreventionService;
import com.richie.component.mfa.validation.service.MfaValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.Set;

/**
 * MFA 验证服务实现
 * <p>
 * 部署位置：richie-gateway-service
 * 职责：实现 MFA 验证相关的核心业务逻辑，供网关层调用
 * <p>
 * 设计原则：
 * <ul>
 *   <li>只读 GlobalCache，零数据库依赖</li>
 *   <li>不处理 HTTP 请求/响应，只返回业务结果</li>
 *   <li>网关层负责根据结果决定如何响应前端</li>
 * </ul>
 *
 * @author richie696
 * @since 5.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MfaValidationServiceImpl implements MfaValidationService {

    /**
     * MFA 统一配置属性
     */
    private final MfaProperties properties;

    /**
     * KMS提供方（可选依赖，用于从 KMS 检索密钥）
     * <p>
     * 如果未注入，则无法从 KMS 检索密钥
     */
    @Autowired(required = false)
    private KeyManagementProvider keyManagementProvider;

    /**
     * TOTP 验证引擎（用于验证 TOTP 验证码）
     */
    private final TotpValidationEngine totpEngine;

    /**
     * 防重放攻击服务（用于防止验证码重复使用）
     */
    private final ReplayAttackPreventionService replayService;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * Spring 事件发布器（用于发布审计事件）
     */
    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    @Override
    public MfaValidationResult checkMfaStatus(String userId, String tenantId, String deviceId) {
        if (StringUtils.isBlank(userId)) {
            return MfaValidationResult.failure("INVALID_USER_ID", "用户ID不能为空");
        }

        // 可信设备校验已下沉至业务登录流程（如 sample-mfa 的 MfaBindManager.isMfaRequiredForLogin），
        // 网关此处仅根据缓存判断是否需 MFA 及返回前端展示用元数据（trustedDeviceSupported 等）

        // 1. 从 GlobalCache 读取用户 MFA 信息
        String cacheKey = MfaKeyUtils.getUserCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());
        MfaUserInfo userInfo = GlobalCache.getObjectFromHash(cacheKey, MfaUserInfo.class);

        if (userInfo == null) {
            // 用户未绑定 MFA，不需要验证
            return MfaValidationResult.successWithoutMfa();
        }

        // 3. 检查账户是否已锁定
        if (userInfo.getLockedUntil() != null && OffsetDateTime.now(ZoneOffset.UTC).isBefore(userInfo.getLockedUntil())) {
            return MfaValidationResult.builder()
                .success(false)
                .mfaRequired(true)
                .mfaBound(true)
                .accountLocked(true)
                .lockedUntil(userInfo.getLockedUntil().toInstant().toEpochMilli())
                .errorCode("ACCOUNT_LOCKED")
                .errorMessage("账户已锁定，请稍后再试")
                .build();
        }

        // 4. 检查 MFA 状态
        if (userInfo.getStatus() == null || userInfo.getStatus() != MfaStatusEnum.ENABLED) {
            // MFA 未启用，不需要验证
            return MfaValidationResult.successWithoutMfa();
        }

        // 5. 需要 MFA 验证
        // 读取可信设备配置信息
        boolean trustedDeviceSupported = properties.getSecurity().getTrustedDevice() != null
            && properties.getSecurity().getTrustedDevice().isEnabled();
        Integer trustedDeviceCount = getTrustedDeviceCount(userId, tenantId);
        Integer maxTrustedDevices = properties.getSecurity().getTrustedDevice() != null
            ? properties.getSecurity().getTrustedDevice().getMaxDevices()
            : 10; // 默认值
        Integer defaultTrustDays = properties.getSecurity().getTrustedDevice() != null
            ? properties.getSecurity().getTrustedDevice().getDefaultTrustDays()
            : 30; // 默认值

        return MfaValidationResult.mfaRequired(trustedDeviceSupported, trustedDeviceCount, maxTrustedDevices, defaultTrustDays);
    }

    @Override
    public MfaValidationResult verifyMfaCode(String userId, String tenantId, String mfaCode) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(mfaCode)) {
            return MfaValidationResult.failure("INVALID_PARAMS", "用户ID和验证码不能为空");
        }

        // 1. 从 GlobalCache 读取用户 MFA 信息
        String cacheKey = MfaKeyUtils.getUserCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());
        MfaUserInfo userInfo = GlobalCache.getObjectFromHash(cacheKey, MfaUserInfo.class);

        if (userInfo == null) {
            // 发布审计事件：用户未绑定
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "FAILED",
                "MFA_NOT_BOUND", "用户未绑定 MFA 设备", null);
            return MfaValidationResult.failure("MFA_NOT_BOUND", "用户未绑定 MFA 设备");
        }

        // 2. 检查 MFA 状态
        if (userInfo.getStatus() == null || userInfo.getStatus() != MfaStatusEnum.ENABLED) {
            // 发布审计事件：MFA 未启用
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "FAILED",
                "MFA_NOT_ENABLED", "MFA 未启用", null);
            return MfaValidationResult.failure("MFA_NOT_ENABLED", "MFA 未启用");
        }

        // 3. 检查账户是否已锁定
        if (userInfo.getLockedUntil() != null && OffsetDateTime.now(ZoneOffset.UTC).isBefore(userInfo.getLockedUntil())) {
            // 发布审计事件：账户已锁定
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "BLOCKED",
                "ACCOUNT_LOCKED", "账户已锁定，请稍后再试", null);
            return MfaValidationResult.builder()
                .success(false)
                .accountLocked(true)
                .lockedUntil(userInfo.getLockedUntil().toInstant().toEpochMilli())
                .errorCode("ACCOUNT_LOCKED")
                .errorMessage("账户已锁定，请稍后再试")
                .build();
        }

        // 4. 防重放检查
        int timeStep = properties.getTotp().getTimeWindow();
        long currentTimeStep = System.currentTimeMillis() / 1000 / timeStep;
        if (replayService.isCodeUsed(userId, tenantId, currentTimeStep)) {
            log.warn("检测到重放攻击，userId: {}, tenantId: {}", userId, tenantId);
            // 发布审计事件：重放攻击
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "FAILED",
                "MFA_CODE_USED", "验证码已被使用（重放攻击）", null);
            return MfaValidationResult.failure("MFA_CODE_USED", "验证码已被使用");
        }

        // 5. TOTP 验证
        try {
            // 从用户信息中获取算法、period、digits（确保与二维码生成时使用的参数一致）
            String algorithm = userInfo.getAlgorithm();
            Integer period = userInfo.getPeriod();
            Integer digits = userInfo.getDigits();

            // 从密钥管理器检索密钥（使用 tenantId 和 userId）
            // 注意：validation 模块不直接依赖 SecretKeyManager，需要通过 KeyManagementProvider 检索
            String secretReference = buildSecretReference(tenantId, userId);
            String plainSecret = retrieveSecretFromKms(secretReference);
            log.info("从密钥管理器检索到的密钥: {} (长度: {})", plainSecret, plainSecret != null ? plainSecret.length() : 0);

            // 使用数据库中的 period 和 digits 进行验证，确保与二维码生成时使用的参数一致
            boolean valid = totpEngine.verifyCode(
                plainSecret,
                mfaCode,
                userId,
                tenantId,
                properties.getTotp().getWindowSize(),
                algorithm,
                period != null ? period : properties.getTotp().getPeriod(),  // 使用数据库中的 period，如果为null则使用配置默认值
                digits != null ? digits : properties.getTotp().getDigits()   // 使用数据库中的 digits，如果为null则使用配置默认值
            );

            if (!valid) {
                // 6. 记录失败次数
                String failureKey = MfaKeyUtils.getFailureCountKey(tenantId, userId, tenantSupport.isTenantEnabled());
                long failureCount = GlobalCache.increment(failureKey, 1, Duration.ofHours(1).toMillis());
                int maxAttempts = properties.getSecurity().getMaxAttempts();

               // 达到最大失败次数，需要锁定账户
               if (failureCount >= maxAttempts) {
                   // 注意：账户锁定通知应通过消息队列或缓存标记实现，validation 模块不直接调用 management 模块
                   // 实现方式：
                   // 1. 通过消息队列发送账户锁定事件（推荐）
                   // 2. 在缓存中标记锁定状态，由 management 模块定期扫描并更新数据库
                   // 3. 通过 Feign 客户端异步调用 management 模块的锁定 API（不推荐，会增加耦合）
                   // 当前实现：在缓存中标记锁定状态，management 模块在查询用户信息时会检查并更新数据库
                   String lockKey = "mfa:lock:%s:%s".formatted(
                       tenantSupport.isTenantEnabled() && tenantId != null ? tenantId + ":" : "",
                       userId);
                   long lockDuration = properties.getSecurity().getLockDurationSeconds() * 1000L;
                   GlobalCache.addStringCache(lockKey, "1", lockDuration);
                   log.warn("账户达到最大失败次数，已标记锁定，tenantId: {}, userId: {}, lockDuration: {}秒",
                       tenantId, userId, properties.getSecurity().getLockDurationSeconds());

                   // 发布审计事件：账户锁定
                   publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "BLOCKED",
                       "ACCOUNT_LOCKED", "账户已锁定", null);

                   return MfaValidationResult.builder()
                       .success(false)
                       .errorCode("MFA_CODE_INVALID")
                       .errorMessage("验证码错误，账户已锁定")
                       .failureCount((int) failureCount)
                       .maxAttempts(maxAttempts)
                       .accountLocked(true)
                       .build();
               }

                // 发布审计事件：验证失败
                publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "FAILED",
                    "MFA_CODE_INVALID", "验证码错误", null);

                return MfaValidationResult.builder()
                    .success(false)
                    .errorCode("MFA_CODE_INVALID")
                    .errorMessage("验证码错误")
                    .failureCount((int) failureCount)
                    .maxAttempts(maxAttempts)
                    .build();
            }

            // 7. 验证成功，标记验证码已使用
            replayService.markCodeAsUsed(userId, tenantId, currentTimeStep);

            // 8. 清除失败计数
            String failureKey = MfaKeyUtils.getFailureCountKey(tenantId, userId, tenantSupport.isTenantEnabled());
            GlobalCache.removeCache(failureKey);

            // 9. 发布审计事件：验证成功
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "SUCCESS", null, null, null);

            log.info("MFA验证成功，userId: {}, tenantId: {}", userId, tenantId);
            return MfaValidationResult.successWithMfa();

        } catch (Exception e) {
            log.error("MFA验证异常，userId: {}, tenantId: {}", userId, tenantId, e);
            // 发布审计事件：验证异常
            publishAuditEvent(tenantId, userId, MfaOperationTypeEnum.VERIFY, "TOTP", null, "FAILED",
                "MFA_VERIFY_ERROR", "MFA验证异常: " + e.getMessage(), null);
            return MfaValidationResult.failure("MFA_VERIFY_ERROR", "MFA验证异常: " + e.getMessage());
        }
    }

    @Override
    public MfaValidationResult checkTrustedDevice(String userId, String tenantId, String deviceId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(deviceId)) {
            return MfaValidationResult.failure("INVALID_PARAMS", "用户ID和设备ID不能为空");
        }

        return checkTrustedDeviceInternal(userId, tenantId, deviceId);
    }

    /**
     * 内部方法：检查可信设备
     * <p>
     * 从 GlobalCache 读取可信设备信息，检查设备是否可信且未过期
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param deviceId 设备ID（必填）
     * @return MFA 验证结果
     * <ul>
     *   <li>如果设备可信且未过期：{@code trustedDevice = true, trustedDeviceExpired = false, mfaRequired = false}</li>
     *   <li>如果设备不存在：{@code trustedDevice = false, mfaRequired = true}</li>
     *   <li>如果设备已过期：{@code trustedDevice = true, trustedDeviceExpired = true, mfaRequired = true}</li>
     * </ul>
     */
    private MfaValidationResult checkTrustedDeviceInternal(String userId, String tenantId, String deviceId) {
        // 从 GlobalCache 读取可信设备信息
        // 注意：management 模块通过 TrustedDeviceManager.syncToCache() 将设备信息同步到缓存
        // 缓存 key 格式：mfa:trusted-device:{tenantId}:{userId}:{deviceId}
        String trustedDeviceKey = MfaKeyUtils.getTrustedDeviceCacheKey(tenantId, userId, deviceId, tenantSupport.isTenantEnabled());

        // 从缓存读取可信设备对象
        MfaTrustedDevice device = GlobalCache.getObjectFromHash(trustedDeviceKey, MfaTrustedDevice.class);

        if (device == null) {
            // 设备不存在或未同步到缓存
            return MfaValidationResult.builder()
                .success(false)
                .mfaRequired(true)
                .trustedDevice(false)
                .build();
        }

        // 检查设备信任是否有效（未过期）
        if (device.getTrustedUntil() == null || OffsetDateTime.now(ZoneOffset.UTC).isAfter(device.getTrustedUntil())) {
            // 设备已过期
            return MfaValidationResult.builder()
                .success(false)
                .mfaRequired(true)
                .trustedDevice(true)
                .trustedDeviceExpired(true)
                .build();
        }

        // 设备可信且未过期，可跳过 MFA
        return MfaValidationResult.builder()
            .success(true)
            .mfaRequired(false)
            .trustedDevice(true)
            .trustedDeviceExpired(false)
            .build();
    }

    /**
     * 获取用户已注册的可信设备数量
     * <p>
     * 从 GlobalCache 读取可信设备ID列表，并过滤掉已过期的设备，返回有效设备数量
     * <p>
     * 注意：management 模块在同步可信设备到缓存时，应该同时维护一个设备ID列表
     * 缓存 key 格式：mfa:trusted-devices:{tenantId}:{userId}，value = Set<String>（设备ID集合）
     * <p>
     * 如果 management 模块未维护列表，则通过遍历缓存 key 的方式统计（性能较差，不推荐）
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @return 用户已注册的有效可信设备数量（已过滤过期设备）
     */
    private Integer getTrustedDeviceCount(String userId, String tenantId) {
        // 从 GlobalCache 读取可信设备ID列表
        // 注意：management 模块应该在同步设备时，同时维护一个设备ID列表到缓存
        String deviceListKey = MfaKeyUtils.getTrustedDeviceListKey(tenantId, userId, tenantSupport.isTenantEnabled());
        Set<String> deviceIds = GlobalCache.getSetCache(deviceListKey, String.class);

        if (deviceIds != null && !deviceIds.isEmpty()) {
            // 过滤掉已过期的设备
            int validCount = 0;
            for (String deviceId : deviceIds) {
                String deviceKey = MfaKeyUtils.getTrustedDeviceCacheKey(tenantId, userId, deviceId, tenantSupport.isTenantEnabled());
                MfaTrustedDevice device = GlobalCache.getObjectFromHash(deviceKey, MfaTrustedDevice.class);
                if (device != null && device.getTrustedUntil() != null
                    && OffsetDateTime.now(ZoneOffset.UTC).isBefore(device.getTrustedUntil())) {
                    validCount++;
                }
            }
            return validCount;
        }

        // 如果列表不存在，返回 0（management 模块应该维护这个列表）
        return 0;
    }

    /**
     * 发布审计事件
     *
     * @param tenantId      租户ID
     * @param userId        用户ID
     * @param operationType 操作类型
     * @param authMethod    认证方式
     * @param deviceId      设备ID
     * @param result        操作结果
     * @param errorCode     错误码
     * @param errorMessage  错误消息
     * @param durationMs    操作耗时
     */
    private void publishAuditEvent(String tenantId, String userId, MfaOperationTypeEnum operationType,
                                   String authMethod, String deviceId, String result,
                                   String errorCode, String errorMessage, Long durationMs) {
        if (eventPublisher == null) {
            return; // 如果未注入事件发布器，跳过发布
        }
        try {
            // 提取 IP 和 User-Agent
            String ipAddress = extractIpAddress();
            String userAgent = extractUserAgent();

            // 构建并发布事件
            MfaAuditEvent event = MfaAuditEvent.builder(this)
                .tenantId(tenantId)
                .userId(userId)
                .operationType(operationType)
                .authMethod(authMethod)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .result(result)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

            eventPublisher.publishEvent(event);

            if (log.isDebugEnabled()) {
                log.debug("MFA审计事件已发布: operationType={}, userId={}, result={}",
                    operationType != null ? operationType.getCode() : null, userId, result);
            }
        } catch (Exception e) {
            // 审计事件发布失败不应影响主流程，只记录错误日志
            log.error("发布MFA审计事件失败: operationType={}, userId={}, result={}",
                operationType != null ? operationType.getCode() : null, userId, result, e);
        }
    }

    /**
     * 提取客户端 IP 地址
     *
     * @return IP 地址，如果无法获取则返回 null
     */
    private String extractIpAddress() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }

            // 优先从 X-Forwarded-For 获取（支持反向代理）
            String ip = request.getHeader("X-Forwarded-For");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                // 多个 IP 时取第一个
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }

            // 尝试其他常见代理头
            ip = request.getHeader("Proxy-Client-IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("WL-Proxy-Client-IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_CLIENT_IP");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            if (StringUtils.isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return ip;
            }

            // 最后使用 RemoteAddr
            return request.getRemoteAddr();
        } catch (Exception e) {
            log.debug("提取IP地址失败", e);
            return null;
        }
    }

    /**
     * 提取 User-Agent
     *
     * @return User-Agent，如果无法获取则返回 null
     */
    private String extractUserAgent() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return null;
            }
            HttpServletRequest request = attributes.getRequest();
            if (request == null) {
                return null;
            }
            return request.getHeader("User-Agent");
        } catch (Exception e) {
            log.debug("提取User-Agent失败", e);
            return null;
        }
    }

    /**
     * 构建密钥引用路径
     * <p>
     * 路径格式：
     * <ul>
     *   <li>有租户：{@code mfa/{tenantId}/{userId}}</li>
     *   <li>无租户：{@code mfa/{userId}}</li>
     * </ul>
     * <p>
     * <b>注意</b>：此方法只检查 tenantId 是否为空，不检查租户功能是否启用。
     * 因为密钥存储时使用的是传入的 tenantId（可能为 null），检索时也应使用相同的逻辑。
     *
     * @param tenantId 租户ID（可选）
     * @param userId   用户ID（必填）
     * @return 密钥引用路径
     */
    private String buildSecretReference(String tenantId, String userId) {
        // 只检查 tenantId 是否为空，不检查租户功能是否启用
        // 因为密钥存储时使用的是传入的 tenantId（可能为 null），检索时也应使用相同的逻辑
        if (tenantId != null && !tenantId.isEmpty()) {
            return "mfa/%s/%s".formatted(tenantId, userId);
        }
        return "mfa/%s".formatted(userId);
    }

    /**
     * 从 KMS 检索密钥
     * <p>
     * 根据密钥引用从 KMS 获取明文密钥。
     *
     * @param secretReference 密钥引用（路径/ID）
     * @return 明文密钥（Base32编码的 TOTP 密钥）
     * @throws RuntimeException 如果检索失败或密钥不存在
     */
    private String retrieveSecretFromKms(String secretReference) {
        if (keyManagementProvider == null || !keyManagementProvider.isAvailable()) {
            log.error("KMS提供方不可用，无法检索密钥，secretReference: {}", secretReference);
            throw new RuntimeException("KMS提供方不可用，无法检索密钥");
        }

        try {
            return keyManagementProvider.retrieveSecret(secretReference);
        } catch (Exception e) {
            log.error("KMS检索密钥失败，secretReference: {}", secretReference, e);
            throw new RuntimeException("KMS检索密钥失败: %s".formatted(e.getMessage()), e);
        }
    }
}
