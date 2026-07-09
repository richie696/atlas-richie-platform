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
package com.richie.component.mfa.validation.replay;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 防重放攻击服务
 * <p>
 * 职责：防止验证码重复使用
 * 限制：可写GlobalCache（标记验证码已使用）
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayAttackPreventionService {

    /** MFA 配置，用于读取 TOTP 时间窗口与防重放 TTL 倍数 */
    private final MfaProperties properties;
    /** 租户支持，用于判断是否启用租户（影响缓存 key 格式） */
    private final MfaTenantSupport tenantSupport;

    /**
     * 检查验证码是否已使用
     * <p>
     * 从 GlobalCache 检查指定时间步长的验证码是否已被标记为已使用
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param timeStep  时间步长（当前时间戳 / 时间窗口）
     * @return 是否已使用
     * <ul>
     *   <li>{@code true}：验证码已被使用（可能是重放攻击）</li>
     *   <li>{@code false}：验证码未被使用</li>
     * </ul>
     */
    public boolean isCodeUsed(String userId, String tenantId, long timeStep) {
        String key = MfaKeyUtils.getReplayPreventionKey(tenantId, userId, timeStep, tenantSupport.isTenantEnabled());
        return GlobalCache.key().hasKey(key);
    }

    /**
     * 标记验证码已使用
     * <p>
     * 在 GlobalCache 中标记指定时间步长的验证码已被使用，防止重放攻击
     * <p>
     * TTL 计算：TTL = 时间窗口 * replayPreventionTtlMultiplier（确保验证码在时间窗口过期后仍能被正确识别为已使用）
     *
     * @param userId   用户ID（必填）
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param timeStep 时间步长（当前时间戳 / 时间窗口）
     */
    public void markCodeAsUsed(String userId, String tenantId, long timeStep) {
        String key = MfaKeyUtils.getReplayPreventionKey(tenantId, userId, timeStep, tenantSupport.isTenantEnabled());

        // TTL = 时间窗口 * replayPreventionTtlMultiplier
        int timeWindow = properties.getTotp().getTimeWindow();
        int ttlMultiplier = properties.getSecurity().getReplayPreventionTtlMultiplier();
        long ttl = ((long) timeWindow * ttlMultiplier) * 1000L; // 转换为毫秒

        GlobalCache.value().set(key, "1", ttl);
        log.debug("标记验证码已使用，key: {}, ttl: {}ms", key, ttl);
    }
}
