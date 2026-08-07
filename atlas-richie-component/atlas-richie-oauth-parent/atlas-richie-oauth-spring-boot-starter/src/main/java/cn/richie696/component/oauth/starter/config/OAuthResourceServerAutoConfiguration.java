package cn.richie696.component.oauth.starter.config;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.client.StandardOAuthTokenClient;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.gateway.OAuthGatewayAdapter;
import cn.richie696.component.oauth.resource.DefaultJwtTokenVerifier;
import cn.richie696.component.oauth.resource.HttpJwkSource;
import cn.richie696.component.oauth.resource.IntrospectionClient;
import cn.richie696.component.oauth.resource.CachingIntrospectionClient;
import cn.richie696.component.oauth.resource.JwkSource;
import cn.richie696.component.oauth.resource.JwtTokenVerifier;
import cn.richie696.component.oauth.resource.ResourceServerAuthenticator;
import cn.richie696.component.oauth.resource.OAuthResourceServerMetrics;
import cn.richie696.component.oauth.resource.DpopProofValidator;
import cn.richie696.component.oauth.resource.DpopReplayStore;
import cn.richie696.component.oauth.resource.OAuthCacheDpopReplayStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

import java.net.URI;
import java.time.Duration;

/**
 * Resource Server 与 Gateway Adapter 的 Spring Boot 自动装配入口。
 *
 * <p>职责链位置：处于 oauth-core / oauth-resource-server / oauth-oidc 等纯领域模块与业务应用之间，
 * 把协议级 bean 通过条件装配暴露为 Spring 容器 bean。它本身不实现任何协议算法，
 * 也不强制依赖 Spring Security；业务应用可自由选择以 Filter、Servlet、Reactor 或其他方式暴露。</p>
 *
 * <p>支持三种明确模式：</p>
 * <ul>
 *     <li>JWT-only：配置 {@code jwk-set-uri}；创建 JWKS 和 JWT verifier。</li>
 *     <li>Introspection-only：配置 {@code introspection-uri} 且保持
 *         {@code introspection-fallback=true}（默认）；不创建 JWT verifier，直接以内省为认证来源。</li>
 *     <li>Hybrid：同时配置两者；优先本地 JWT 校验，失败后回源 introspection。</li>
 * </ul>
 * <p>两者都未配置时组件仍可启动，但认证会 fail-closed；生产环境应在配置校验阶段拒绝这种配置。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OAuthResourceServerProperties.class)
public class OAuthResourceServerAutoConfiguration {

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    public OAuthResourceServerMetrics oauthResourceServerMetrics(MeterRegistry registry) {
        return new OAuthResourceServerMetrics(registry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "dpop-enabled", havingValue = "true")
    @ConditionalOnMissingBean(DpopReplayStore.class)
    public DpopReplayStore oauthDpopReplayStore(OAuthCache cache) {
        return new OAuthCacheDpopReplayStore(cache);
    }

    @Bean
    @ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "dpop-enabled", havingValue = "true")
    @ConditionalOnMissingBean(DpopProofValidator.class)
    public DpopProofValidator oauthDpopProofValidator(OAuthResourceServerProperties properties,
                                                       DpopReplayStore replayStore) {
        return new DpopProofValidator(replayStore,
                Duration.ofSeconds(properties.getDpopClockSkewSeconds()), properties.getDpopNonce());
    }

    @Bean
    @ConditionalOnMissingBean(OAuthCache.class)
    public OAuthCache oauthInMemoryCache() {
        return new InMemoryOAuthCache();
    }

    @Bean
    @ConditionalOnMissingBean(JwkSource.class)
    @ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "jwk-set-uri")
    public JwkSource oauthJwkSource(OAuthResourceServerProperties properties, OAuthCache cache) {
        return new HttpJwkSource(URI.create(properties.getJwkSetUri()), cache,
                Duration.ofSeconds(properties.getTimeoutSeconds()),
                Duration.ofSeconds(properties.getJwksCacheTtlSeconds()));
    }

    @Bean
    @ConditionalOnMissingBean(JwtTokenVerifier.class)
    @ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "jwk-set-uri")
    public JwtTokenVerifier oauthJwtTokenVerifier(OAuthResourceServerProperties properties, JwkSource jwkSource) {
        return new DefaultJwtTokenVerifier(properties.getIssuer(), properties.getRequiredAudience(), jwkSource);
    }

    @Bean
    @ConditionalOnMissingBean(IntrospectionClient.class)
    @ConditionalOnProperty(prefix = "platform.oauth.resource-server", name = "introspection-uri")
    public IntrospectionClient oauthIntrospectionClient(OAuthResourceServerProperties properties,
                                                        OAuthCache cache) {
        StandardOAuthTokenClient client = new StandardOAuthTokenClient(
                null, URI.create(properties.getIntrospectionUri()),
                properties.getIntrospectionClientId(), properties.getIntrospectionClientSecret(),
                Duration.ofSeconds(properties.getTimeoutSeconds()));
        IntrospectionClient delegate = client::introspect;
        return new CachingIntrospectionClient(cache, delegate,
                properties.getIntrospectionCacheTtlSeconds() * 1_000L);
    }

    @Bean
    @ConditionalOnMissingBean(ResourceServerAuthenticator.class)
    public ResourceServerAuthenticator oauthResourceServerAuthenticator(
            ObjectProvider<JwtTokenVerifier> verifier,
            OAuthResourceServerProperties properties,
            ObjectProvider<IntrospectionClient> introspectionClient,
            ObjectProvider<OAuthResourceServerMetrics> metrics,
            ObjectProvider<DpopProofValidator> dpopProofValidator
    ) {
        return new ResourceServerAuthenticator(verifier.getIfAvailable(), introspectionClient.getIfAvailable(),
                properties.isIntrospectionFallback(), metrics.getIfAvailable(), dpopProofValidator.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(OAuthGatewayAdapter.class)
    public OAuthGatewayAdapter oauthGatewayAdapter(ResourceServerAuthenticator authenticator) {
        return new OAuthGatewayAdapter(authenticator);
    }
}
