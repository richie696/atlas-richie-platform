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
package com.richie.component.mfa.management.manager;

import com.richie.component.mfa.core.config.MfaProperties;
import com.richie.component.mfa.core.config.properties.MfaKeyManagementProperties;
import com.richie.component.mfa.core.config.properties.MfaVaultProperties;
import com.richie.component.mfa.core.constant.KeyManagementProviderEnum;
import com.richie.component.mfa.core.crypto.provider.VaultKeyManagementEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 密钥轮换管理器。
 *
 * <p>职责：按照 Vault 轮换配置与当前密钥管理提供方配置，
 * 调用底层 Vault 实现执行主密钥轮换操作，并对其他提供方给出轮换建议日志。</p>
 *
 * <p>当前实现：</p>
 * <ul>
 *   <li><b>Vault</b>：调用 {@link VaultKeyManagementEngine#rotateKey()} 触发 Transit Key 版本轮换</li>
 *   <li><b>Cloud KMS / HSM / Local</b>：暂不在组件内直接管理主密钥版本，仅记录提示日志，实际轮换由云厂商/HSM/配置中心完成</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *   <li>本管理器不自带调度能力，建议由上层业务通过定时任务或运维脚本显式调用 {@link #rotateMasterKey()}</li>
 *   <li>轮换仅影响“主密钥版本”，历史密文的重加密由业务/运维按需通过后台任务渐进完成</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KeyRotationManager {

    /**
     * MFA统一配置属性（用于获取密钥管理配置）
     */
    private final MfaProperties properties;

    /**
     * Vault密钥管理引擎提供者（可选依赖，仅在配置为VAULT时注入）
     */
    private final ObjectProvider<VaultKeyManagementEngine> vaultKeyManagementEngineProvider;

    /**
     * 触发一次主密钥轮换
     * <p>
     * 根据配置的密钥管理提供方，执行相应的主密钥轮换操作
     * <p>
     * 支持的提供方：
     * <ul>
     *   <li>VAULT：调用 VaultKeyManagementEngine.rotateKey() 触发 Transit Key 版本轮换</li>
     *   <li>KMS：记录提示日志，建议在云厂商控制台配置自动轮换</li>
     *   <li>HSM：记录提示日志，建议由 HSM 管理工具/安全团队执行</li>
     *   <li>LOCAL：记录提示日志，建议通过配置中心更新本地密钥后重启服务</li>
     * </ul>
     * <p>
     * 注意：本方法不自带调度能力，建议由上层业务通过定时任务或运维脚本显式调用
     * <p>
     * 轮换仅影响"主密钥版本"，历史密文的重加密由业务/运维按需通过后台任务渐进完成
     */
    public void rotateMasterKey() {
        MfaKeyManagementProperties keyManagement = properties.getSecurity().getKeyManagement();
        KeyManagementProviderEnum provider = keyManagement.getProvider();
        log.info("开始执行主密钥轮换，provider: {}", provider);

        switch (provider) {
            case VAULT -> rotateVaultKey(keyManagement.getVault());
            case KMS -> {
                // 云 KMS 主密钥通常在云厂商控制台或基础设施侧配置自动轮换策略
                log.info("Cloud KMS 场景建议在云厂商控制台配置自动轮换，本组件暂不直接调用各云厂商的轮换 API，当前仅记录轮换触发事件");
            }
            case HSM -> {
                // HSM 主密钥轮换通常由安全/运维团队通过 HSM 管理工具执行
                log.info("HSM 场景建议由 HSM 管理工具/安全团队执行主密钥轮换，本组件暂不直接管理 HSM 密钥版本，当前仅记录轮换触发事件");
            }
            case LOCAL -> {
                // 本地模拟加密仅用于开发/测试环境，主密钥来自配置文件
                log.info("本地加密（LOCAL）模式不建议在组件内自动轮换主密钥，如有需要请通过配置中心更新本地密钥后重启服务");
            }
            default -> log.warn("未知的密钥管理提供方: {}，跳过主密钥轮换", provider);
        }
    }

    /**
     * 执行 Vault 主密钥轮换
     * <p>
     * 调用 VaultKeyManagementEngine.rotateKey() 触发 Transit Key 版本轮换
     *
     * @param vaultProperties Vault配置属性（必填）
     * @throws RuntimeException 如果 VaultKeyManagementEngine 未注入或轮换失败
     */
    private void rotateVaultKey(MfaVaultProperties vaultProperties) {
        if (vaultProperties == null || vaultProperties.getRotation() == null) {
            log.info("Vault 密钥轮换未配置（vault.rotation 缺失），跳过主密钥轮换逻辑");
            return;
        }

        var vaultKeyManagementEngine = vaultKeyManagementEngineProvider.getIfAvailable();
        if (vaultKeyManagementEngine == null) {
            log.error("VaultKeyManagementEngine 未注入，无法执行 Vault 主密钥轮换，请检查配置：provider 是否为 vault");
            return;
        }

        try {
            var rotation = vaultProperties.getRotation();
            log.info("执行 Vault 主密钥轮换，rotationIntervalDays: {}, gracePeriodDays: {}",
                rotation.getRotationIntervalDays(), rotation.getGracePeriodDays());
            vaultKeyManagementEngine.rotateKey();
        } catch (Exception e) {
            log.error("执行 Vault 主密钥轮换失败", e);
            throw e;
        }
    }
}

