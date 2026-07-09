/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.mfa.management.manager;

import com.richie.context.utils.data.JsonUtils;
import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.component.mfa.management.dto.MfaStatusResponse;
import com.richie.component.mfa.management.mapper.MfaUserMapper;
import tools.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.StructuredTaskScope;

/**
 * MFA状态管理器
 * <p>
 * 职责：查询MFA状态
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MfaStatusManager {

    /**
     * MFA用户信息Mapper（数据库操作）
     */
    private final MfaUserMapper mfaUserMapper;

    /**
     * MFA统一配置属性
     */
    private final MfaProperties properties;

    /**
     * 可信设备管理器（查询可信设备数量）
     */
    private final TrustedDeviceManager trustedDeviceManager;

    /**
     * 租户支持类（用于判断是否启用租户功能）
     */
    private final MfaTenantSupport tenantSupport;

    /**
     * 查询MFA状态
     * <p>
     * 使用结构化并发优化：并行查询MFA用户信息和可信设备数量，减少总耗时。
     * <p>
     * 性能优化说明：
     * <ul>
     *   <li>原实现：串行执行两个数据库查询，总耗时 = 查询1耗时 + 查询2耗时</li>
     *   <li>优化后：并行执行两个独立的数据库查询，总耗时 ≈ max(查询1耗时, 查询2耗时)</li>
     *   <li>预期性能提升：约50%（当两个查询耗时相同时）</li>
     * </ul>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
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
     * @throws RuntimeException 如果并行查询被中断或执行失败
     */
    @SuppressWarnings("preview")
    public MfaStatusResponse getStatus(String tenantId, String userId) {
        // 如果未启用租户，tenantId可以为null
        String actualTenantId = tenantSupport.isTenantEnabled() ? tenantId : null;

        try (var scope = StructuredTaskScope.open()) {
            // 并行查询：MFA用户信息和可信设备数量（两个独立的数据库查询，无数据依赖）
            var userInfoTask = scope.fork(() ->
                mfaUserMapper.selectByTenantAndUser(actualTenantId, userId));
            var trustedDevicesTask = scope.fork(() ->
                trustedDeviceManager.countTrustedDevices(actualTenantId, userId));

            scope.join(); // JDK 25: 全部成功则返回，任一失败则抛出 FailedException

            MfaUserInfo userInfo = userInfoTask.get();
            int trustedDevices = trustedDevicesTask.get();

            if (userInfo == null) {
                return MfaStatusResponse.builder()
                    .userId(userId)
                    .status(MfaStatusEnum.NOT_BOUND)
                    .trustedDevices(0)
                    .backupCodesRemaining(0)
                    .build();
            }

            // 如果状态为null，默认返回未激活
            MfaStatusEnum status = userInfo.getStatus() != null ? userInfo.getStatus() : MfaStatusEnum.NOT_ACTIVATED;

            // 计算剩余备份码数量（本地计算，依赖userInfo）
            int backupCodesRemaining = calculateBackupCodesRemaining(userInfo.getBackupCodesHashed());

            return MfaStatusResponse.builder()
                .userId(userId)
                .status(status)
                .deviceType(userInfo.getDeviceType())
                .bindTime(userInfo.getBindTime())
                .lastUsedTime(userInfo.getLastUsedTime())
                .trustedDevices(trustedDevices)
                .backupCodesRemaining(backupCodesRemaining)
                .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("并行查询MFA状态被中断，tenantId: {}, userId: {}", tenantId, userId, e);
            throw new RuntimeException("查询MFA状态失败", e);
        } catch (StructuredTaskScope.FailedException e) {
            log.error("并行查询MFA状态执行失败，tenantId: {}, userId: {}", tenantId, userId, e);
            throw new RuntimeException("查询MFA状态失败", e.getCause() != null ? e.getCause() : e);
        }
    }

    /**
     * 计算剩余备份码数量
     * <p>
     * 从哈希后的备份码JSON字符串中解析出备份码列表，返回数量
     * <p>
     * 注意：此方法仅统计备份码数量，不验证备份码是否已被使用
     *
     * @param backupCodesHashed 哈希后的备份码JSON字符串（从数据库读取）
     * @return 剩余备份码数量
     * <ul>
     *   <li>如果 backupCodesHashed 为空或null，返回 0</li>
     *   <li>如果解析失败，返回 0</li>
     *   <li>否则返回备份码列表的大小</li>
     * </ul>
     */
    private int calculateBackupCodesRemaining(String backupCodesHashed) {
        if (StringUtils.isBlank(backupCodesHashed)) {
            return 0;
        }

        try {
            // 反序列化JSON数组为List<String>
            List<String> hashedCodes = JsonUtils.getInstance().deserialize(
                backupCodesHashed,
                new TypeReference<List<String>>() {}
            );
            return hashedCodes != null ? hashedCodes.size() : 0;
        } catch (Exception e) {
            log.warn("解析备份码JSON失败，backupCodesHashed: {}", backupCodesHashed, e);
            return 0;
        }
    }
}
