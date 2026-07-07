package com.richie.component.web.core.config.login;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

/**
 * 登录 / Token 签发配置（前缀：{@code platform.component.web.login}）。
 * <p>
 * 由 {@link com.richie.component.web.core.adapter.IssueTokenAdvice} 消费：
 * <ul>
 *   <li>{@link #loginUrls}：命中路径即触发 token 签发</li>
 *   <li>{@link #tokenSecret}：HMAC 共享密钥</li>
 *   <li>{@link #tokenExpirationDate}：token 有效期（毫秒）</li>
 * </ul>
 *
 * <h2>与 Web 主配置的关系</h2>
 * <p>本类绑定 {@code platform.component.web.login}，作为 {@link com.richie.component.web.core.config.WebProperties}
 * 的子域配置被 {@code @NestedConfigurationProperty} 引用（见 WebProperties）。
 *
 * <h2 style="color:#c00">⚠ Gateway 模式下必须禁用</h2>
 * <p>当部署 <strong>atlas-richie-gateway-service</strong> 时，token 签发由 gateway 端
 * {@code atlas-richie-gateway-service} 的过滤器链完成；web-core 端的
 * {@link com.richie.component.web.core.adapter.IssueTokenAdvice} 整个被旁路
 * （参见其 javadoc &lt;作用域&gt; 段落）。
 * <p><strong>YAML 必须保持本子域为空 / 留空</strong>，否则会出现双签发冲突
 * （同一请求被签发两次 token，下游校验时与 redis 中存储的 token 不一致）。
 * <pre>{@code
 * platform:
 *   component:
 *     web:
 *       login:                  # gateway 模式下整段不写或留空即可
 * }</pre>
 *
 * @author richie696
 * @since 2026-07
 */
@Data
@ConfigurationProperties(prefix = "platform.component.web.login")
public class LoginConfig {

    /**
     * 登录地址清单（命中即触发 token 签发）。
     */
    private Set<String> loginUrls;

    /**
     * 令牌签发密钥（HMAC 共享密钥）。
     */
    private String tokenSecret;

    /**
     * 令牌有效时长（毫秒）。
     */
    private long tokenExpirationDate;
}