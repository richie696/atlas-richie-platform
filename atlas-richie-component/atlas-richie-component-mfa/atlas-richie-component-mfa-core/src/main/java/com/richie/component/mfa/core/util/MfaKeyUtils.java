package com.richie.component.mfa.core.util;

/**
 * MFA缓存Key工具类
 * <p>
 * 职责：统一管理MFA相关的缓存Key生成逻辑，支持租户可选
 * <p>
 * 使用场景：
 * <ul>
 *   <li>management 模块：生成缓存Key用于同步数据到缓存</li>
 *   <li>validation 模块：生成缓存Key用于读取缓存数据</li>
 * </ul>
 *
 * @author richie696
 * @since 5.0.0
 */
public class MfaKeyUtils {

    /**
     * 生成用户MFA信息缓存Key
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getUserCacheKey(String tenantId, String userId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:user:%s:%s".formatted(tenantId, userId);
        } else {
            return "mfa:user:%s".formatted(userId);
        }
    }

    /**
     * 生成失败计数缓存Key
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getFailureCountKey(String tenantId, String userId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:failures:%s:%s".formatted(tenantId, userId);
        } else {
            return "mfa:failures:%s".formatted(userId);
        }
    }

    /**
     * 生成防重放攻击缓存Key
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param timeStep 时间步长
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getReplayPreventionKey(String tenantId, String userId, long timeStep, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:used:%s:%s:%d".formatted(tenantId, userId, timeStep);
        } else {
            return "mfa:used:%s:%d".formatted(userId, timeStep);
        }
    }

    /**
     * 生成同步锁Key
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getSyncLockKey(String tenantId, String userId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:sync:lock:%s:%s".formatted(tenantId, userId);
        } else {
            return "mfa:sync:lock:%s".formatted(userId);
        }
    }

    /**
     * 生成可信设备缓存Key
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param deviceId 设备ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getTrustedDeviceCacheKey(String tenantId, String userId, String deviceId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:trusted-device:%s:%s:%s".formatted(tenantId, userId, deviceId);
        } else {
            return "mfa:trusted-device:%s:%s".formatted(userId, deviceId);
        }
    }

    /**
     * 生成可信设备列表缓存Key（用于存储用户的所有可信设备ID列表）
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getTrustedDeviceListKey(String tenantId, String userId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:trusted-devices:%s:%s".formatted(tenantId, userId);
        } else {
            return "mfa:trusted-devices:%s".formatted(userId);
        }
    }

    /**
     * 生成密钥存储缓存Key（用于存储用户的MFA密钥）
     *
     * @param tenantId 租户ID（可为null）
     * @param userId 用户ID
     * @param enableTenant 是否启用租户
     * @return 缓存Key
     */
    public static String getSecretKeyCacheKey(String tenantId, String userId, boolean enableTenant) {
        if (enableTenant && tenantId != null && !tenantId.isEmpty()) {
            return "mfa:secret:%s:%s".formatted(tenantId, userId);
        } else {
            return "mfa:secret:%s".formatted(userId);
        }
    }
}
