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
package com.richie.gateway.config;

import com.richie.component.oauth.core.config.OAuth2Properties;
import com.richie.contract.gateway.config.DeployConfig;
import com.richie.contract.gateway.config.GatewayContract;
import com.richie.contract.gateway.config.TenantFilterConfig;
import com.richie.contract.gateway.config.TokenFilterConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.io.Serializable;

/**
 * 网关配置类（gateway 工程内部聚合类）
 * <p>
 * 本类职责：承载 gateway 工程内部专属的配置（ECC 加密、SSO、异常检测、熔断策略、接口鉴权、
 * 防重复提交、硬件指纹、安全防护等）。这些配置不会跨服务共享，仅由 gateway 自身的 filter
 * 和 service 层使用。
 * <p>
 * 跨服务共享的配置（令牌黑白名单、租户开关、灰度策略、审计总开关）已抽离到
 * {@link GatewayContract}，位于 {@code richie-contract} 模块。本类通过组合持有
 * {@link GatewayContract}，并将 {@link #getToken()} / {@link #getTenant()} /
 * {@link #getDeploy()} / {@link #isAuditEnabled()} 代理到契约聚合类，确保 gateway
 * 内部代码（约 30+ 处）的方法调用签名不变，迁移零改动。
 * <p>
 * 绑定前缀 {@code platform.gateway} 涵盖 gateway 内部专属字段；跨服务共享字段已抽离到
 * {@link GatewayContract}（绑定前缀 {@code platform.gateway.contract}），YAML 树中
 * {@code contract.*} 是 {@code platform.gateway.*} 的子树，两者互不重叠，因此同一根前缀
 * 被两个 {@link ConfigurationProperties} 绑定互不干扰。application.yml / Nacos 中
 * 跨服务共享字段需放置在 {@code platform.gateway.contract.*} 下。
 *
 * @author richie696
 * @version 2.0
 * @since 2022-10-08
 */
@Data
@Configuration
@RefreshScope
@NoArgsConstructor
@ToString(exclude = "contract")
@EqualsAndHashCode(exclude = "contract")
@ConfigurationProperties(prefix = "platform.gateway")
public class GatewayConfig implements Serializable {

    /**
     * 访问记录缓存路径
     */
    private String visitRecordPath = "platform:gateway:visit:";

    /**
     * 安全过滤器配置
     */
    private SecurityFilterConfig security = new SecurityFilterConfig();

    /**
     * OAuth2.0 配置（适用于第三方接口、feign接口的鉴权通信）
     */
    private OAuth2Properties oauth2 = new OAuth2Properties();

    /**
     * SSO配置
     */
    private SsoConfig sso = new SsoConfig();

    /**
     * ECC加密配置
     */
    private EccCryptoConfig eccCrypto = new EccCryptoConfig();

    /**
     * 防重复提交配置
     */
    private DuplicateSubmitConfig duplicateSubmit = new DuplicateSubmitConfig();

    /**
     * 降级响应配置
     */
    private FallbackConfig fallback = new FallbackConfig();

    /**
     * 硬件指纹配置
     */
    private HardwareFingerprintConfig hardwareFingerprint = new HardwareFingerprintConfig();

    /**
     * CSP（Content-Security-Policy）过滤器配置
     */
    private CspFilterConfig csp = new CspFilterConfig();

    /**
     * 跨服务共享契约（令牌 / 租户 / 灰度 / 审计）。
     * <p>
     * 通过 {@link Autowired} 注入，{@code transient} + {@link JsonIgnore} 避免序列化时
     * 级联把整个契约对象序列化进来（GatewayConfig 实现了 Serializable）。
     */
    @Autowired
    @JsonIgnore
    private transient GatewayContract contract;

    // ------------------------------------------------------------------
    // 下列 getter 将共享契约字段"平展"到 GatewayConfig 对象上，保证 gateway 工程
    // 内部遗留代码 gatewayConfig.getToken() / .getTenant() / .getDeploy() /
    // .isAuditEnabled() 的调用签名完全兼容。
    // ------------------------------------------------------------------

    /**
     * 获取令牌过滤器配置（代理到共享契约）
     *
     * @return 令牌过滤器配置
     */
    public TokenFilterConfig getToken() {
        return contract.getToken();
    }

    /**
     * 获取租户过滤器配置（代理到共享契约）
     *
     * @return 租户过滤器配置
     */
    public TenantFilterConfig getTenant() {
        return contract.getTenant();
    }

    /**
     * 获取灰度发布配置（代理到共享契约）
     *
     * @return 灰度发布配置
     */
    public DeployConfig getDeploy() {
        return contract.getDeploy();
    }

    /**
     * 是否启用审计日志功能（代理到共享契约）
     *
     * @return true 表示启用
     */
    public boolean isAuditEnabled() {
        return contract.isAuditEnabled();
    }
}
