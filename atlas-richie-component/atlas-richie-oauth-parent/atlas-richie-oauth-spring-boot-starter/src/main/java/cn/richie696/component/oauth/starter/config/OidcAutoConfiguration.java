package cn.richie696.component.oauth.starter.config;

import cn.richie696.component.oauth.oidc.OidcAuthorizationRequestValidator;
import cn.richie696.component.oauth.oidc.OidcBackchannelLogoutService;
import cn.richie696.component.oauth.oidc.OidcFrontchannelLogoutService;
import cn.richie696.component.oauth.oidc.OidcIdTokenService;
import cn.richie696.component.oauth.oidc.OidcIdTokenSigner;
import cn.richie696.component.oauth.oidc.OidcLogoutTokenSigner;
import cn.richie696.component.oauth.oidc.OidcProviderMetadataService;
import cn.richie696.component.oauth.oidc.OidcUserInfoProvider;
import cn.richie696.component.oauth.oidc.OidcUserInfoService;
import cn.richie696.component.oauth.oidc.OidcAuthorizationResponseService;
import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** OIDC 领域能力自动装配；实际 Controller、用户和 MFA 仍由 OAuth Service 提供。 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "platform.oauth.oidc", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OidcProperties.class)
public class OidcAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OidcAuthorizationRequestValidator oidcAuthorizationRequestValidator(OidcProperties properties) {
        return new OidcAuthorizationRequestValidator(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OidcProviderMetadataService oidcProviderMetadataService(OidcProperties properties) {
        return new OidcProviderMetadataService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OidcAuthorizationResponseService oidcAuthorizationResponseService(OidcProperties properties) {
        return new OidcAuthorizationResponseService(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public OidcFrontchannelLogoutService oidcFrontchannelLogoutService() {
        return new OidcFrontchannelLogoutService();
    }

    @Bean
    @ConditionalOnBean(OidcLogoutTokenSigner.class)
    @ConditionalOnMissingBean
    public OidcBackchannelLogoutService oidcBackchannelLogoutService(OidcLogoutTokenSigner signer) {
        return new OidcBackchannelLogoutService(signer);
    }

    @Bean
    @ConditionalOnBean(OidcUserInfoProvider.class)
    @ConditionalOnMissingBean
    public OidcUserInfoService oidcUserInfoService(OidcProperties properties,
                                                   OidcUserInfoProvider provider) {
        return new OidcUserInfoService(properties, provider);
    }

    @Bean
    @ConditionalOnBean(OidcIdTokenSigner.class)
    @ConditionalOnMissingBean
    public OidcIdTokenService oidcIdTokenService(OidcProperties properties,
                                                 OidcIdTokenSigner signer) {
        return new OidcIdTokenService(properties, signer);
    }
}
