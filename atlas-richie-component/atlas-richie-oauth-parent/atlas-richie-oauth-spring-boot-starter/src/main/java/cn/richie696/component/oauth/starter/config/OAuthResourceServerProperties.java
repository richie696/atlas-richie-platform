package cn.richie696.component.oauth.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Resource Server 与 Gateway Adapter 的统一配置属性，绑定 {@code platform.oauth.resource-server.*}。
 *
 * <p>职责链位置：作为 Spring Boot 配置接入层，与 {@link OAuthResourceServerAutoConfiguration} 配对使用。
 * 它把 Resource Server 模式（JWT-only / Introspection-only / Hybrid）、缓存 TTL、DPoP 开关、超时等
 * 调参聚合在一个属性类中，避免散落在多个 {@code @Value} 或分散的 properties。</p>
 *
 * <p>解决以下问题：业务方需要在不写 Java 代码的情况下切换本地 JWT 校验与远端 introspection 策略，
 * 调整缓存时效以平衡新鲜度与负载，并按需启用 DPoP 证明校验；所有调参点都集中在此处并以
 * {@link org.springframework.boot.context.properties.ConfigurationProperties} 暴露，
 * 配合 IDE 元数据即可获得自动补全与校验。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@Data
@ConfigurationProperties(prefix = "platform.oauth.resource-server")
public class OAuthResourceServerProperties {

    private boolean enabled = false;
    private String issuer;
    private String jwkSetUri;
    private String requiredAudience;
    private String introspectionUri;
    private String introspectionClientId;
    private String introspectionClientSecret;
    private boolean introspectionFallback = true;
    private long jwksCacheTtlSeconds = 600;
    private long introspectionCacheTtlSeconds = 30;
    private int timeoutSeconds = 5;
    private boolean dpopEnabled = false;
    private long dpopClockSkewSeconds = 300;
    private String dpopNonce;
}
