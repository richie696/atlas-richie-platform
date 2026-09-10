/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.gateway.config;

import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * ECC加密配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Data
@Accessors(chain = true)
@RefreshScope
@Configuration
@ConfigurationProperties(prefix = "platform.gateway.ecc-crypto")
public class EccCryptoConfig {

    /**
     * 是否启用ECC加密
     */
    private boolean enabled = false;

    /**
     * 开发环境明文降级开关。
     * <p>仅当 Spring Profile 为 {@code dev} 或 {@code test} 时才会被 Gateway 采用；
     * 生产及其他环境一律忽略，避免把临时 HTTP 调试策略带入正式环境。</p>
     */
    private boolean allowPlaintextInDev = false;

    /**
     * 网关私钥（Base64编码）
     * 如果不配置，将自动生成
     */
    private String gatewayPrivateKey;

    /**
     * 网关公钥（Base64编码）
     * 如果不配置，将自动生成
     */
    private String gatewayPublicKey;

    /**
     * 需要加密的路径模式
     * 支持Ant风格路径匹配
     */
    private String[] encryptPaths = {"/api/**"};

    /**
     * 精确的加密路由规则。只有路径与 HTTP 方法同时匹配时才启用加密。
     * <p>优先于 {@link #encryptPaths} 使用；保留旧字段仅用于已有部署的平滑升级。</p>
     */
    private List<EncryptionRule> encryptRules = new ArrayList<>();

    /**
     * 不需要加密的路径模式
     * 支持Ant风格路径匹配
     */
    private String[] excludePaths = {"/api/health/**", "/api/public/**"};

    /**
     * 网关私钥过期时间（单位：小时）
     * <p style="color: red">如果不配置，将使用默认值6小时，此值不宜过长也不宜过短，请根据实际业务使用中的客户反馈来进行调整。
     */
    private int gatewayKeyExpire = 6;
    /**
     * 客户端公钥缓存过期时间（单位：秒）
     */
    private long clientKeyCacheExpire = 3600;

    @Data
    public static class EncryptionRule {
        /** 支持 Ant 风格匹配的请求路径。 */
        private String path;

        /**
         * 允许的 HTTP 方法。为空时表示所有方法；通常应显式填写以避免将查询接口纳入加密。
         */
        private List<String> methods = new ArrayList<>();
    }

}
