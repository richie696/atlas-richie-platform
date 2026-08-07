package cn.richie696.component.oauth.starter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Resource Server 和 Gateway Adapter 配置。 */
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
