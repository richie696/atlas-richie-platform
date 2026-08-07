package cn.richie696.component.oauth.starter.config;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.gateway.OAuthGatewayAdapter;
import cn.richie696.component.oauth.oidc.OidcAuthorizationRequestValidator;
import cn.richie696.component.oauth.oidc.OidcFrontchannelLogoutService;
import cn.richie696.component.oauth.oidc.OidcProviderMetadataService;
import cn.richie696.component.oauth.resource.DpopProofValidator;
import cn.richie696.component.oauth.resource.DpopReplayStore;
import cn.richie696.component.oauth.resource.IntrospectionClient;
import cn.richie696.component.oauth.resource.JwkSource;
import cn.richie696.component.oauth.resource.JwtTokenVerifier;
import cn.richie696.component.oauth.resource.ResourceServerAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthStarterAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    OAuthResourceServerAutoConfiguration.class,
                    OidcAutoConfiguration.class));

    @Test
    void resourceServerAndOidcBeansAreCreatedWhenEnabled() {
        contextRunner.withPropertyValues(
                        "platform.oauth.resource-server.enabled=true",
                        "platform.oauth.resource-server.issuer=https://issuer.example",
                        "platform.oauth.resource-server.jwk-set-uri=https://issuer.example/jwks",
                        "platform.oauth.resource-server.dpop-enabled=true",
                        "platform.oauth.oidc.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(OAuthCache.class);
                    assertThat(context).hasSingleBean(JwkSource.class);
                    assertThat(context).hasSingleBean(ResourceServerAuthenticator.class);
                    assertThat(context).hasSingleBean(OAuthGatewayAdapter.class);
                    assertThat(context).hasSingleBean(OidcAuthorizationRequestValidator.class);
                    assertThat(context).hasSingleBean(OidcProviderMetadataService.class);
                    assertThat(context).hasSingleBean(OidcFrontchannelLogoutService.class);
                    assertThat(context).hasSingleBean(DpopReplayStore.class);
                    assertThat(context).hasSingleBean(DpopProofValidator.class);
                });
    }

    @Test
    void disabledByDefaultCreatesNoOAuthBeans() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(OAuthCache.class);
            assertThat(context).doesNotHaveBean(ResourceServerAuthenticator.class);
            assertThat(context).doesNotHaveBean(OidcAuthorizationRequestValidator.class);
        });
    }

    @Test
    void introspectionOnlyModeCreatesIntrospectionWithoutJwtVerifier() {
        contextRunner.withPropertyValues(
                        "platform.oauth.resource-server.enabled=true",
                        "platform.oauth.resource-server.issuer=https://issuer.example",
                        "platform.oauth.resource-server.introspection-uri=https://issuer.example/introspect",
                        "platform.oauth.resource-server.introspection-client-id=resource-client",
                        "platform.oauth.resource-server.introspection-client-secret=secret")
                .run(context -> {
                    assertThat(context).hasSingleBean(IntrospectionClient.class);
                    assertThat(context).hasSingleBean(ResourceServerAuthenticator.class);
                    assertThat(context).doesNotHaveBean(JwtTokenVerifier.class);
                    assertThat(context).doesNotHaveBean(JwkSource.class);
                });
    }

    @Test
    void hybridModeCreatesBothJwtAndIntrospectionPaths() {
        contextRunner.withPropertyValues(
                        "platform.oauth.resource-server.enabled=true",
                        "platform.oauth.resource-server.issuer=https://issuer.example",
                        "platform.oauth.resource-server.jwk-set-uri=https://issuer.example/jwks",
                        "platform.oauth.resource-server.introspection-uri=https://issuer.example/introspect")
                .run(context -> {
                    assertThat(context).hasSingleBean(JwkSource.class);
                    assertThat(context).hasSingleBean(JwtTokenVerifier.class);
                    assertThat(context).hasSingleBean(IntrospectionClient.class);
                    assertThat(context).hasSingleBean(ResourceServerAuthenticator.class);
                });
    }
}
