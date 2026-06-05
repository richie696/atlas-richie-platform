package com.richie.component.mfa.management.manager;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MFA缓存同步管理器
 * <p>
 * 职责：数据库变更后同步到GlobalCache
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaCacheSyncManager {

    /**
     * MFA统一配置属性（用于获取TTL配置）
     */
    private final MfaProperties properties;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * 同步到缓存
     * <p>
     * 将 MFA 用户信息同步到 GlobalCache（Hash结构），供 validation 模块读取
     * <p>
     * 缓存 key 格式：{@code mfa:user:{tenantId}:{userId}} 或 {@code mfa:user:{userId}}
     * <p>
     * TTL：根据配置的 {@code ttlHours} 计算（转换为毫秒）
     *
     * @param userInfo MFA用户信息（必填，包含用户ID、租户ID、密钥、状态等信息）
     */
    public void syncToCache(MfaUserInfo userInfo) {
        String cacheKey = MfaKeyUtils.getUserCacheKey(
            userInfo.getTenantId(),
            userInfo.getUserId(),
            tenantSupport.isTenantEnabled()
        );

        long ttl = properties.getManagement().getTtlHours() * 3600 * 1000L; // 转换为毫秒
        GlobalCache.struct().set(cacheKey, userInfo, ttl);

        log.info("MFA信息同步到缓存成功，userId: {}, tenantId: {}",
            userInfo.getUserId(), userInfo.getTenantId());
    }

    /**
     * 从缓存删除
     * <p>
     * 从 GlobalCache 删除指定用户的 MFA 信息（通常在解绑时调用）
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     */
    public void removeFromCache(String tenantId, String userId) {
        String cacheKey = MfaKeyUtils.getUserCacheKey(tenantId, userId, tenantSupport.isTenantEnabled());
        GlobalCache.key().removeCache(cacheKey);
        log.info("MFA信息从缓存删除成功，userId: {}, tenantId: {}", userId, tenantId);
    }
}
