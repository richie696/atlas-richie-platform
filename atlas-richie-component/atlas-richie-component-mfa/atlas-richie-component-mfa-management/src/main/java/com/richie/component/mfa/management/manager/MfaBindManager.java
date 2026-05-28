package com.richie.component.mfa.management.manager;

import com.richie.context.common.api.LoginUserContextHolder;
import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.constant.MfaOperationTypeEnum;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.management.dto.LoginMfaCheckResult;
import com.richie.component.mfa.management.dto.MfaBindResult;
import com.richie.component.mfa.management.mapper.MfaUserMapper;
import com.richie.component.mfa.validation.dto.MfaValidationResult;
import com.richie.component.mfa.validation.engine.TotpValidationEngine;
import com.richie.component.mfa.validation.service.MfaValidationService;
import com.richie.component.mfa.management.util.MfaAuditEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import tools.jackson.core.type.TypeReference;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * MFA绑定管理器
 *
 * <p><b>设计原则</b>：只依赖业务系统User表的主键ID，不维护任何用户信息。
 * 所有方法只需要传入userId（业务系统User表的主键ID）即可。
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaBindManager {

    /**
     * MFA用户信息Mapper（数据库操作）
     */
    private final MfaUserMapper mfaUserMapper;

    /**
     * 密钥管理器（生成、加密、解密密钥）
     */
    private final SecretKeyManager secretKeyManager;

    /**
     * 备份码管理器（生成、哈希备份码）
     */
    private final BackupCodeManager backupCodeManager;

    /**
     * 二维码管理器（生成二维码URL）
     */
    private final QrCodeManager qrCodeManager;

    /**
     * 缓存同步管理器（同步数据到GlobalCache）
     */
    private final MfaCacheSyncManager cacheSyncManager;

    /**
     * MFA统一配置属性
     */
    private final MfaProperties properties;

    /**
     * TOTP验证引擎（验证TOTP验证码）
     */
    private final TotpValidationEngine totpValidationEngine;

    /**
     * 可信设备管理器（注册可信设备）
     */
    private final TrustedDeviceManager trustedDeviceManager;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * MFA 验证服务（仅用于 TOTP 校验，本类负责备份码校验并包装为统一入口）
     */
    private final MfaValidationService mfaValidationService;

    /**
     * Spring 事件发布器（用于发布审计事件）
     */
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 审计事件发布工具（延迟初始化，避免 RequestContextHolder 在非 Web 环境不可用）
     */
    private MfaAuditEventPublisher auditEventPublisher;

    /**
     * 初始化审计事件发布工具
     */
    @PostConstruct
    public void initAuditEventPublisher() {
        this.auditEventPublisher = new MfaAuditEventPublisher(eventPublisher, this);
    }

    /**
     * 绑定MFA设备
     * <p>
     * 为指定用户生成 MFA 密钥、二维码和备份码，并保存到数据库（状态为未激活）
     * <p>
     * 执行流程：
     * <ol>
     *   <li>生成密钥（使用 SecretKeyManager）</li>
     *   <li>生成备份码（使用 BackupCodeManager）</li>
     *   <li>生成二维码（使用 QrCodeManager）</li>
     *   <li>检查是否已存在（唯一性校验）</li>
     *   <li>保存到数据库（状态为 NOT_ACTIVATED）</li>
     *   <li>同步到 GlobalCache</li>
     * </ol>
     *
     * @param tenantId   租户ID（可选，如果未启用租户则为 null）
     * @param userId     用户ID（必填，业务系统User表的主键ID）
     * @param deviceType 设备类型（必填，例如 "TOTP"）
     * @return 绑定结果（包含二维码、密钥、备份码）
     * <ul>
     *   <li>{@code qrCodeUrl}：二维码图片URL（用于前端显示）</li>
     *   <li>{@code secretKey}：密钥（Base32编码，用于手动输入）</li>
     *   <li>{@code backupCodes}：备份码列表（用于紧急情况，仅返回一次）</li>
     *   <li>{@code expiresIn}：二维码有效期（秒，默认600秒）</li>
     * </ul>
     * @throws IllegalStateException 如果用户已绑定 MFA 设备
     */
    @Transactional
    public MfaBindResult bindDevice(String tenantId, String userId, String deviceType) {
        // 1. 生成密钥
        String plainSecret = secretKeyManager.generateSecretKey();

        // 调试日志：记录生成的密钥信息
        log.info("=== MFA绑定密钥生成调试信息 ===");
        log.info("tenantId: {}, userId: {}", tenantId, userId);
        log.info("生成的 plainSecret: {}", plainSecret);
        log.info("算法: {}, 位数: {}, 时间窗口: {}",
            properties.getTotp().getAlgorithm(),
            properties.getTotp().getDigits(),
            properties.getTotp().getPeriod());
        log.info("=================================");

        // 2. 存储密钥到 KMS（返回密钥引用，不存储加密后的密钥到数据库）
        String secretReference = secretKeyManager.storeSecret(tenantId, userId, plainSecret);
        log.info("密钥已存储到 KMS，secretReference: {}", secretReference);

        // 3. 生成备份码
        List<String> plainBackupCodes = backupCodeManager.generateBackupCodes(
                properties.getSecurity().getBackupCode().getCount()
        );
        List<String> hashedBackupCodes = backupCodeManager.hashBackupCodes(plainBackupCodes);

        // 4. 生成二维码（包含完整的 TOTP 参数，确保与 Microsoft Authenticator 等应用兼容）
        // 从上下文获取用户登录名，如果获取不到则使用 userId 作为备选
        String username = null;
        try {
            username = LoginUserContextHolder.getUserInfo().getUsername();
        } catch (Exception e) {
            log.warn("无法从上下文获取用户登录名，使用 userId 作为备选，userId: {}", userId, e);
        }
        if (StringUtils.isBlank(username)) {
            username = userId; // 如果获取不到用户名，使用 userId 作为备选
        }

        String qrCodeUrl = qrCodeManager.generateQrCodeUrl(
                tenantId,
                userId,
                username,
                plainSecret,
                properties.getManagement().getIssuer(),
                properties.getTotp().getAlgorithm(),
                properties.getTotp().getDigits(),
                properties.getTotp().getPeriod()
        );

        // 5. 检查是否已存在（唯一性校验）
        // 注意：当 tenant_id 为 NULL 时，MySQL 的唯一索引 (tenant_id, user_id) 无法保证 user_id 唯一
        // 因为 NULL != NULL，所以需要应用层检查
        // 如果未启用租户，tenant_id为NULL，需要检查是否有任何 tenant_id IS NULL 的记录
        // 如果启用租户，通过uk_tenant_user保证(tenant_id, user_id)唯一
        MfaUserInfo existing = mfaUserMapper.selectByTenantAndUser(tenantId, userId);
        if (existing != null && existing.getStatus() == MfaStatusEnum.ENABLED) {
            log.warn("MFA设备已绑定，tenantId: {}, userId: {}", tenantId, userId);
            // 如果已存在，删除刚存储的密钥（回滚）
            try {
                secretKeyManager.deleteSecret(tenantId, userId);
            } catch (Exception e) {
                log.error("回滚密钥存储失败，secretReference: {}", secretReference, e);
            }
            throw new IllegalStateException("MFA设备已绑定，请先解绑后再绑定");
        }

        // 6. 保存到数据库（事务）：不保存密钥，密钥存储在密钥管理器中
        MfaUserInfo userInfo = new MfaUserInfo();
        userInfo.setTenantId(tenantId);  // 如果未启用租户，则为null
        userInfo.setUserId(userId);
        // 注意：不再保存 secretKeyEncrypted 字段，密钥存储在密钥管理器中（Redis/Vault等）
        userInfo.setBackupCodesHashed(JsonUtils.getInstance().serialize(hashedBackupCodes));
        userInfo.setDeviceType(deviceType);
        userInfo.setAlgorithm(properties.getTotp().getAlgorithm());
        userInfo.setDigits(properties.getTotp().getDigits());
        userInfo.setPeriod(properties.getTotp().getPeriod());
        userInfo.setStatus(MfaStatusEnum.NOT_ACTIVATED);  // 未激活
        userInfo.setBindTime(OffsetDateTime.now(ZoneOffset.UTC));

        mfaUserMapper.insert(userInfo);

        // 7. 同步到GlobalCache（Redis HMSET是原子操作，不需要额外锁）
        cacheSyncManager.syncToCache(userInfo);

        // 8. 发布审计事件
        if (auditEventPublisher != null) {
            auditEventPublisher.publishSuccess(tenantId, userId, MfaOperationTypeEnum.BIND, deviceType, null);
        }

        // 9. 返回结果
        return MfaBindResult.builder()
                .qrCodeUrl(qrCodeUrl)
                .secretKey(plainSecret)  // 仅返回一次
                .backupCodes(plainBackupCodes)  // 仅返回一次
                .expiresIn(600)  // 10分钟有效期
                .build();
    }

    /**
     * 激活MFA设备
     * <p>
     * 验证用户输入的验证码，如果正确则激活 MFA 设备（状态改为已启用）
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从数据库查询用户 MFA 信息</li>
     *   <li>解密密钥（使用 SecretKeyManager）</li>
     *   <li>验证 TOTP 验证码（使用 TotpValidationEngine）</li>
     *   <li>更新状态为已启用（ENABLED）</li>
     *   <li>同步到 GlobalCache</li>
     *   <li>如果用户选择信任设备，注册为可信设备（使用 TrustedDeviceManager）</li>
     * </ol>
     *
     * @param tenantId          租户ID（可选，如果未启用租户则为 null）
     * @param userId            用户ID（必填，业务系统User表的主键ID）
     * @param code              TOTP验证码（必填，6位数字）
     * @param deviceId          设备ID（可选，用于注册可信设备）
     * @param deviceName        设备名称（可选，用于显示，例如 "Chrome on Windows"）
     * @param deviceFingerprint 设备指纹（可选，用于审计，原始指纹的哈希）
     * @param trustDevice       是否信任此设备（可选，true表示注册为可信设备）
     * @return 是否激活成功
     * <ul>
     *   <li>{@code true}：激活成功</li>
     *   <li>{@code false}：激活失败（验证码错误或用户信息不存在）</li>
     * </ul>
     */
    @Transactional
    public boolean activateDevice(String tenantId, String userId, String code,
                                  String deviceId, String deviceName, String deviceFingerprint, Boolean trustDevice) {
        // 1. 从数据库查询（如果未启用租户，tenantId可以为null）
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        MfaUserInfo userInfo = mfaUserMapper.selectByTenantAndUser(actualTenantId, userId);
        if (userInfo == null) {
            log.warn("MFA用户信息不存在，tenantId: {}, userId: {}", tenantId, userId);
            // 发布审计事件：激活失败（用户信息不存在）
            if (auditEventPublisher != null) {
                auditEventPublisher.publishFailure(tenantId, userId, MfaOperationTypeEnum.ACTIVATE, "TOTP", deviceId,
                    "MFA_NOT_BOUND", "MFA用户信息不存在");
            }
            return false;
        }

        // 调试日志：记录查询到的用户信息
        log.info("=== MFA激活验证调试信息 ===");
        log.info("查询参数 - tenantId: {}, userId: {}, actualTenantId: {}", tenantId, userId, actualTenantId);
        log.info("查询结果 - id: {}, tenantId: {}, userId: {}, algorithm: {}, digits: {}, period: {}",
            userInfo.getId(), userInfo.getTenantId(), userInfo.getUserId(),
            userInfo.getAlgorithm(), userInfo.getDigits(), userInfo.getPeriod());
        log.info("用户输入的验证码: {}", code);
        log.info("===========================");

        // 调用TOTP验证引擎进行验证
        // window参数：允许±windowSize个时间窗口的容错（约±30秒，取决于配置的时间窗口）
        // 从用户信息中获取算法、period、digits（确保与二维码生成时使用的参数一致）
        String algorithm = userInfo.getAlgorithm();
        Integer period = userInfo.getPeriod();
        Integer digits = userInfo.getDigits();
        int window = properties.getTotp().getWindowSize();

        // 从密钥管理器检索密钥（使用 tenantId 和 userId）
        String plainSecret = secretKeyManager.retrieveSecret(actualTenantId, userId);
        log.info("从密钥管理器检索到的密钥: {} (长度: {})", plainSecret, plainSecret != null ? plainSecret.length() : 0);

        // 使用数据库中的 period 和 digits 进行验证，确保与二维码生成时使用的参数一致
        boolean valid = totpValidationEngine.verifyCode(
                plainSecret,  // 传递明文密钥（从 KMS 检索）
                code,
                userId,
                actualTenantId,
                window,
                algorithm,  // 传递算法参数，确保与二维码生成时使用的算法一致
                period != null ? period : properties.getTotp().getPeriod(),  // 使用数据库中的 period，如果为null则使用配置默认值
                digits != null ? digits : properties.getTotp().getDigits()   // 使用数据库中的 digits，如果为null则使用配置默认值
        );

        if (!valid) {
            log.warn("MFA激活验证失败，tenantId: {}, userId: {}", tenantId, userId);
            // 发布审计事件：激活失败（验证码错误）
            if (auditEventPublisher != null) {
                auditEventPublisher.publishFailure(tenantId, userId, MfaOperationTypeEnum.ACTIVATE, "TOTP", deviceId,
                    "MFA_CODE_INVALID", "验证码错误");
            }
            return false;
        }

        // 3. 更新状态为已激活
        // 使用 LambdaUpdateWrapper 显式更新字段，确保状态和激活时间被正确更新
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<MfaUserInfo> updateWrapper =
            new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
        updateWrapper.eq(MfaUserInfo::getId, userInfo.getId())
                .set(MfaUserInfo::getStatus, MfaStatusEnum.ENABLED)
                .set(MfaUserInfo::getActivatedTime, now);

        int updateCount = mfaUserMapper.update(null, updateWrapper);
        if (updateCount <= 0) {
            log.error("更新MFA状态失败，可能记录不存在或已被删除，tenantId: {}, userId: {}, id: {}, 更新行数: {}",
                tenantId, userId, userInfo.getId(), updateCount);
            return false;
        }

        log.debug("MFA状态更新成功，tenantId: {}, userId: {}, id: {}, 更新行数: {}, 新状态: {}, 激活时间: {}",
            tenantId, userId, userInfo.getId(), updateCount, MfaStatusEnum.ENABLED, now);

        // 更新内存中的对象，用于后续缓存同步
        userInfo.setStatus(MfaStatusEnum.ENABLED);
        userInfo.setActivatedTime(now);

        // 4. 同步到GlobalCache
        cacheSyncManager.syncToCache(userInfo);

        // 5. 如果用户选择信任设备，注册为可信设备
        if (Boolean.TRUE.equals(trustDevice) && deviceId != null && !deviceId.isEmpty()) {
            try {
                trustedDeviceManager.registerTrustedDevice(
                        actualTenantId,
                        userId,
                        deviceId,
                        deviceName,
                        deviceFingerprint
                );
                log.info("设备已注册为可信设备，tenantId: {}, userId: {}, deviceId: {}",
                        tenantId, userId, deviceId);
            } catch (Exception e) {
                // 设备信任注册失败不影响激活流程，只记录警告
                log.warn("设备信任注册失败，但不影响激活，tenantId: {}, userId: {}, deviceId: {}",
                        tenantId, userId, deviceId, e);
            }
        }

        log.info("MFA设备激活成功，tenantId: {}, userId: {}", tenantId, userId);

        // 发布审计事件：激活成功
        if (auditEventPublisher != null) {
            auditEventPublisher.publishSuccess(tenantId, userId, MfaOperationTypeEnum.ACTIVATE, "TOTP", deviceId);
        }

        return true;
    }

    /**
     * 激活MFA设备（重载方法，兼容旧代码）
     * <p>
     * 此方法不处理设备信任，仅验证验证码并激活设备
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param code      TOTP验证码（必填，6位数字）
     * @return 是否激活成功
     * <ul>
     *   <li>{@code true}：激活成功</li>
     *   <li>{@code false}：激活失败（验证码错误或用户信息不存在）</li>
     * </ul>
     */
    @Transactional
    public boolean activateDevice(String tenantId, String userId, String code) {
        return activateDevice(tenantId, userId, code, null, null, null, false);
    }

    /**
     * 判断指定用户是否已绑定MFA设备
     * <p>
     * 查询数据库中是否存在该用户的MFA绑定记录（未删除的记录）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 是否已绑定MFA设备
     * <ul>
     *   <li>{@code true}：已绑定（存在未删除的MFA绑定记录）</li>
     *   <li>{@code false}：未绑定（不存在或所有记录都已删除）</li>
     * </ul>
     */
    private boolean isMfaBound(String tenantId, String userId) {
        // 如果未启用租户，tenantId可以为null
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;

        // 查询未删除的MFA绑定记录
        MfaUserInfo userInfo = mfaUserMapper.selectByTenantAndUser(actualTenantId, userId);
        return userInfo != null && userInfo.getStatus() == MfaStatusEnum.ENABLED;
    }

    /**
     * 判断本次登录是否需要 MFA 验证（在查询 MFA 绑定表之前先校验可信设备）
     * <p>
     * 供业务系统（如 sample-mfa）在登录流程中调用：先根据请求头中的 deviceId（及可选 hardwareFingerprint）
     * 校验是否为可信设备；若是可信设备且未过期，则直接返回 false（不需要 MFA）；否则再查 MFA 绑定表。
     *
     * @param tenantId           租户ID（可选，如果未启用租户则为 null）
     * @param userId             用户ID（必填）
     * @param deviceId           设备ID（请求头 X-Device-Id，可选）
     * @return 是否需要 MFA 验证：false 表示不需要（可信设备或未绑定 MFA），true 表示需要
     */
    public boolean isTrustedDevice(String tenantId, String userId, String deviceId) {
        // 若提供了 deviceId 且当前设备为可信设备且未过期，则不需要 MFA
        if (StringUtils.isNotBlank(deviceId) && trustedDeviceManager.isTrustedDevice(tenantId, userId, deviceId)) {
            trustedDeviceManager.updateLastUsedTime(tenantId, userId, deviceId);
            return true;
        }
        // 仅当用户已绑定 MFA 时才需要 MFA 验证；新注册用户未绑定则不需要
        return false;
    }

    /**
     * 登录时 MFA 校验（一次调用返回所需结果，供业务根据对象判断）
     *
     * @param tenantId           租户ID（可选，如果未启用租户则为 null）
     * @param userId             用户ID（必填）
     * @param deviceId           设备ID（请求头 X-Device-Id，可选）
     * @return 登录 MFA 校验结果（mfaRequired、mfaBound）
     */
    public LoginMfaCheckResult checkLoginMfa(String tenantId, String userId, String deviceId) {
        boolean trustedDevice = isTrustedDevice(tenantId, userId, deviceId);
        boolean mfaBound = isMfaBound(tenantId, userId);
        return LoginMfaCheckResult.builder()
            .mfaRequired(mfaBound && !trustedDevice)
            .mfaBound(mfaBound)
            .build();
    }

    /**
     * 解绑MFA设备
     * <p>
     * 删除用户的 MFA 配置（逻辑删除），并清除缓存
     * <p>
     * 执行流程：
     * <ol>
     *   <li>从数据库查询用户 MFA 信息</li>
     *   <li>逻辑删除（使用 MyBatis-Plus 的 deleteById）</li>
     *   <li>从 GlobalCache 删除</li>
     * </ol>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @return 是否解绑成功
     * <ul>
     *   <li>{@code true}：解绑成功</li>
     *   <li>{@code false}：解绑失败（用户信息不存在）</li>
     * </ul>
     */
    @Transactional
    public boolean unbindDevice(String tenantId, String userId) {
        // 1. 从数据库查询（如果未启用租户，tenantId可以为null）
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;

        // 查询所有相关记录（包括已删除的），确保全部清理
        List<MfaUserInfo> allRecords = mfaUserMapper.selectAllByTenantAndUser(actualTenantId, userId);
        if (allRecords.isEmpty()) {
            log.warn("MFA用户信息不存在，tenantId: {}, userId: {}", tenantId, userId);
            // 发布审计事件：解绑失败（用户信息不存在）
            if (auditEventPublisher != null) {
                auditEventPublisher.publishFailure(tenantId, userId, MfaOperationTypeEnum.UNBIND, null, null,
                    "MFA_NOT_BOUND", "MFA用户信息不存在");
            }
            return false;
        }

        // 2. 逻辑删除所有相关记录（防止重复绑定导致的遗留数据）
        int deletedCount = 0;
        for (MfaUserInfo record : allRecords) {
            // 只删除未删除的记录
            if (record.getDeleted() == null || !record.getDeleted()) {
                mfaUserMapper.deleteById(record.getId());
                deletedCount++;
            }
        }

        // 3. 从密钥管理器删除密钥（使用 tenantId 和 userId）
        try {
            secretKeyManager.deleteSecret(actualTenantId, userId);
            log.debug("密钥已从密钥管理器删除，tenantId: {}, userId: {}", actualTenantId, userId);
        } catch (Exception e) {
            // 删除失败不影响解绑流程，只记录警告（可能密钥已被删除）
            log.warn("从密钥管理器删除密钥失败，但不影响解绑，tenantId: {}, userId: {}",
                actualTenantId, userId, e);
        }

        // 4. 从缓存删除
        cacheSyncManager.removeFromCache(actualTenantId, userId);

        // 5. 发布审计事件：解绑成功
        if (auditEventPublisher != null) {
            auditEventPublisher.publishSuccess(tenantId, userId, MfaOperationTypeEnum.UNBIND, null, null);
        }

        log.info("MFA设备解绑成功，tenantId: {}, userId: {}, 删除了 {} 条记录", tenantId, userId, deletedCount);
        return true;
    }

    /**
     * 校验 MFA 验证码（统一入口：先试备份码，再走 TOTP）
     * <p>
     * 供业务系统（如 sample-mfa、richie-general-service）在登录等流程中调用。
     * validation 包不访问数据库，故备份码校验放在 management 本包；TOTP 仍委托 validation 的 {@link MfaValidationService}。
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，未启用租户则为 null）
     * @param mfaCode  用户输入的验证码（6 位 TOTP 或 8 位备份码）
     * @return 校验结果，成功时 {@link MfaValidationResult#isSuccess()} 为 true
     */
    @Transactional(rollbackFor = Exception.class)
    public MfaValidationResult verifyMfaCode(String userId, String tenantId, String mfaCode) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(mfaCode)) {
            return MfaValidationResult.failure("INVALID_PARAMS", "用户ID和验证码不能为空");
        }
        // 8 位视为备份码，由 management 直接校验并消耗（访问数据库）
        if (mfaCode.length() == 8) {
            try {
                if (verifyAndConsumeBackupCode(tenantId, userId, mfaCode)) {
                    log.info("备份码验证成功，userId: {}, tenantId: {}", userId, tenantId);
                    // 发布审计事件：备份码验证成功
                    if (auditEventPublisher != null) {
                        auditEventPublisher.publishSuccess(tenantId, userId, MfaOperationTypeEnum.VERIFY, "BACKUP_CODE", null);
                    }
                    return MfaValidationResult.successWithMfa();
                } else {
                    // 发布审计事件：备份码验证失败
                    if (auditEventPublisher != null) {
                        auditEventPublisher.publishFailure(tenantId, userId, MfaOperationTypeEnum.VERIFY, "BACKUP_CODE", null,
                            "BACKUP_CODE_INVALID", "备份码无效");
                    }
                }
            } catch (Exception e) {
                log.warn("备份码验证异常，继续尝试 TOTP，userId: {}, tenantId: {}", userId, tenantId, e);
                // 发布审计事件：备份码验证异常
                if (auditEventPublisher != null) {
                    auditEventPublisher.publishFailure(tenantId, userId, MfaOperationTypeEnum.VERIFY, "BACKUP_CODE", null,
                        "BACKUP_CODE_ERROR", "备份码验证异常: " + e.getMessage());
                }
            }
        }
        // 备份码未命中或非 8 位：委托 validation 做 TOTP 校验（不访问 DB，仅 KMS + 缓存）

        return mfaValidationService.verifyMfaCode(userId, tenantId, mfaCode);
    }

    /**
     * 验证并消耗备份码
     * <p>
     * 用户输入 8 位备份码时调用，验证通过则从列表中移除该码（一次性使用），并更新数据库与缓存。
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填）
     * @param code     用户输入的备份码（8 位数字）
     * @return 是否验证并消耗成功
     */
    @Transactional
    public boolean verifyAndConsumeBackupCode(String tenantId, String userId, String code) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(code)) {
            return false;
        }

        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;
        MfaUserInfo userInfo = mfaUserMapper.selectByTenantAndUser(actualTenantId, userId);
        if (userInfo == null || StringUtils.isBlank(userInfo.getBackupCodesHashed())) {
            return false;
        }

        List<String> hashedCodes;
        try {
            hashedCodes = JsonUtils.getInstance().deserialize(
                userInfo.getBackupCodesHashed(),
                new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            log.warn("解析备份码 JSON 失败，userId: {}", userId, e);
            return false;
        }

        if (hashedCodes == null || hashedCodes.isEmpty()) {
            return false;
        }

        String matchedHash = null;
        for (String hashedCode : hashedCodes) {
            if (backupCodeManager.verifyBackupCode(code, hashedCode)) {
                matchedHash = hashedCode;
                break;
            }
        }

        if (matchedHash == null) {
            return false;
        }

        List<String> remaining = new ArrayList<>(hashedCodes);
        remaining.remove(matchedHash);
        userInfo.setBackupCodesHashed(JsonUtils.getInstance().serialize(remaining));
        mfaUserMapper.updateById(userInfo);
        cacheSyncManager.syncToCache(userInfo);

        log.info("备份码验证并消耗成功，tenantId: {}, userId: {}, 剩余备份码数: {}", tenantId, userId, remaining.size());
        return true;
    }
}
