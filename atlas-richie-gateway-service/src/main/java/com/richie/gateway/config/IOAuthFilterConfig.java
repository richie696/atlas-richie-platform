package com.richie.gateway.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 接口授权过滤器配置（第三方系统 OAuth2.0 认证）
 *
 * @author richie696
 * @version 1.0
 * @since 2023-08-02 00:52:24
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "platform.gateway.interface-auth")
public class IOAuthFilterConfig {

    /**
     * 是否启用过滤器（true：启用，false：禁用[默认]）
     */
    private boolean enable = false;

    /**
     * OAuth2.0 Token 签发密钥【推荐长度：32位】（与自有系统密钥分离）
     * <p>
     * <strong>必填项</strong>：当 {@link #enable} 为 {@code true} 时，此字段必须配置，否则应用启动将失败
     */
    private String tokenSecret;

    /**
     * access_token 默认有效期（小时，默认：2）
     */
    private Integer defaultTokenValidDuration = 2;

    /**
     * refresh_token 默认有效期（小时，默认：720，即30天）
     */
    private Integer defaultRefreshTokenValidDuration = 720;

    /**
     * 是否启用立即作废功能（默认：false）
     * <p>
     * 当设置为 {@code true} 时，每次调用签发令牌接口（client_credentials grant type）时，
     * 会自动作废该客户端之前的所有 refresh_token 和 access_token，确保同一客户端在同一时刻只有一对有效令牌。
     * <p>
     * 注意：
     * <ul>
     *   <li>启用此功能后，如果客户端在多个地方同时使用令牌，可能会导致其他地方的令牌被意外作废</li>
     *   <li>建议仅在安全要求较高的场景下启用，或确保客户端能够及时获取新的令牌</li>
     * </ul>
     */
    private boolean revokePreviousTokensOnIssue = false;

    /**
     * 是否启用签发令牌接口的每日调用次数限制（默认：true）
     * <p>
     * 限流规则：
     * <ul>
     *   <li>按客户端维度统计每日调用次数</li>
     *   <li>每日最大调用次数与 {@link #defaultTokenValidDuration} 成反比，令牌越长效，可接受的日调用次数越少</li>
     *   <li>超过当日上限后，将拒绝新的令牌签发请求</li>
     * </ul>
     * <p>
     * 计算公式：
     * <pre>
     * base = max(24 / defaultTokenValidDuration, 1)
     * maxIssuesPerDay = base + 2
     * </pre>
     * <p>
     * 示例：
     * <ul>
     *   <li>当 {@code defaultTokenValidDuration = 1} 小时时，每日最多可签发 26 次（24 + 2）</li>
     *   <li>当 {@code defaultTokenValidDuration = 2} 小时时，每日最多可签发 14 次（12 + 2）</li>
     *   <li>当 {@code defaultTokenValidDuration = 24} 小时时，每日最多可签发 3 次（1 + 2）</li>
     * </ul>
     * <p>
     * 注意：如果客户端配置了自定义的 {@code tokenValidDuration}，则使用客户端配置值进行计算。
     */
    private boolean enableDailyIssueLimit = true;

    /**
     * 默认构造函数
     */
    public IOAuthFilterConfig() {
    }
}
