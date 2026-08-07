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
package cn.richie696.component.oauth.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth 2.1 组件的统一配置属性。
 * <p>
 * 配置前缀 {@code platform.component.oauth};承载组件级开关(enabled)、签名密钥(tokenSecret)、
 * Token TTL、issuer/audience、每日签发限额、撤销前序 token 开关、错误文档基地址以及 RFC 8628
 * Device Authorization 相关参数。
 * </p>
 * <p>
 * 处于整个 oauth 组件的配置入口位置:由 {@link OAuth2AutoConfiguration} 通过
 * {@code @EnableConfigurationProperties} 绑定;TokenEndpoint、HmacAccessTokenSigner、
 * DeviceAuthorizationService 等运行时直接读取所需字段,OAuth Service 可在子类化时补充业务字段。
 * </p>
 * <p>
 * 解决的问题:把组件的所有可调参数收敛到一份 ConfigurationProperties,既支持 IDE 自动补全与 YAML
 * 校验,也让"是否启用 OAuth"这种开关无需业务方写自定义 @ConditionalOnExpression。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
@Data
@ConfigurationProperties(prefix = "platform.component.oauth")
public class OAuth2Properties {

    /**
     * 是否启用 OAuth 2.1 组件（默认：false）
     */
    private boolean enabled = false;

    /**
     * Token 签发密钥（推荐长度：32 位）
     * <p>
     * 启用组件时必填
     */
    private String tokenSecret;

    /** access token 的 issuer，生产环境必须配置为稳定的 AS 标识。 */
    private String issuer = "Richie Inc.";

    /** access token 默认 audience/resource。 */
    private String audience;

    /**
     * access_token 默认有效期（小时，默认：2）
     */
    private Integer defaultTokenValidDuration = 2;

    /**
     * refresh_token 默认有效期（小时，默认：720，即 30 天）
     */
    private Integer defaultRefreshTokenValidDuration = 720;

    /**
     * 是否启用签发新令牌时立即作废旧令牌（默认：false）
     * <p>
     * 启用后，每次 client_credentials 签发时自动作废该客户端之前的所有令牌，
     * 确保同一客户端同时只有一对有效令牌。
     */
    private boolean revokePreviousTokensOnIssue = false;

    /**
     * 是否启用每日签发次数限制（默认：true）
     * <p>
     * 按客户端维度统计，每日最大次数与 token 有效期成反比。
     * 计算公式：base = max(24 / tokenValidDuration, 1)，maxIssuesPerDay = base + 2
     */
    private boolean enableDailyIssueLimit = true;

    /**
     * OAuth2.0 错误文档基础 URI（可选）
     * <p>
     * 配置后，OAuth2.0 错误响应中将包含 {@code error_uri} 字段指向具体的错误文档页面。
     * 未配置时，错误响应中不包含 {@code error_uri} 字段。
     * <p>
     * <p>
     * 示例配置：
     * <pre>{@code
     * platform:
     *   component:
     *     oauth:
     *       error-doc-base-uri: "https://docs.example.com/oauth2/errors#"
     * }</pre>
     */
    private String errorDocsBaseUri;

    /** RFC 8628 Device Authorization 配置。 */
    private String deviceVerificationUri;
    private long deviceExpiresInSeconds = 600;
    private long devicePollingIntervalSeconds = 5;
}
