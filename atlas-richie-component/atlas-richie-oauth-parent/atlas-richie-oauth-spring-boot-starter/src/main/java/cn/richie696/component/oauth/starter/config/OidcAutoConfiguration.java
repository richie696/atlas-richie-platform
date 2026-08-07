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

/**
 * OIDC 领域能力的 Spring Boot 自动装配入口，与 OAuth Service 互补。
 *
 * <p>职责链位置：处于 oauth-oidc 纯协议模块与业务应用之间。它负责把 OIDC 协议级 bean
 * （元数据、授权请求校验、Front/Backchannel Logout、ID Token 服务、UserInfo 服务等）
 * 装配为 Spring 容器 bean；HTTP Controller、用户数据源与 MFA 流程由 OAuth Service 提供，
 * 不在本装配类的职责范围内。</p>
 *
 * <p>解决以下问题：OIDC 服务端能力分散在多个领域类里，缺少统一的 Spring 接入；
 * 通过条件装配（{@code @ConditionalOnBean} / {@code @ConditionalOnMissingBean}）让用户
 * 既能零配置启用协议核心，也能在已有实现时优雅让位；同时通过 {@link OidcProperties} 提供 issuer、
 * 签名算法等可调参数，使业务服务能在不写 Java 代码的情况下接入 OIDC。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
