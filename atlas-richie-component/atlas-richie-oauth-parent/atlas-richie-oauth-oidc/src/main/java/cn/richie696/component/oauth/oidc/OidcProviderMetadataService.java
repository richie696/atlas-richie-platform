package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;

/**
 * 把 {@link cn.richie696.component.oauth.oidc.config.OidcProperties} 装配成
 * {@link OidcProviderMetadata} 的纯协议层服务。
 *
 * <p>处于 OAuth Service 与 {@link OidcProviderMetadata} 数据模型之间：上游只读
 * {@code OidcProperties}，下游产出不可变的 Discovery record。它不注册路由、不写
 * 响应体、不感知 Spring MVC 或 WebFlux，HTTP 暴露由 OAuth Service 在启动时把
 * {@code metadata()} 的结果写到 {@code /.well-known/openid-configuration} 端点。
 *
 * <p>解决"Discovery JSON 在 Controller 里手写、字段拼写漂移且单元测试难以覆盖"的
 * 维护问题，把 OIDC Provider 声明能力（支持的 scope、grant、response_type、
 * logout 能力）映射为一次对象构造，便于在启动期一次性校验"声明的能力是否真的
 * 被本 AS 实现"。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcProviderMetadataService {

    private final OidcProperties properties;

    public OidcProviderMetadataService(OidcProperties properties) {
        this.properties = properties;
    }

    public OidcProviderMetadata metadata() {
        return new OidcProviderMetadata(
                properties.getIssuer(),
                properties.getAuthorizationEndpoint(),
                properties.getTokenEndpoint(),
                properties.getDeviceAuthorizationEndpoint(),
                properties.getUserInfoEndpoint(),
                properties.getJwksUri(),
                properties.getEndSessionEndpoint(),
                properties.getResponseTypesSupported(),
                properties.getGrantTypesSupported(),
                properties.getSubjectTypesSupported(),
                properties.getScopesSupported(),
                properties.getClaimsSupported(),
                properties.getTokenEndpointAuthMethodsSupported(),
                properties.getCodeChallengeMethodsSupported(),
                properties.getIdTokenSigningAlgValuesSupported(),
                properties.getResponseModesSupported(),
                properties.isFrontchannelLogoutSupported(),
                properties.isFrontchannelLogoutSessionSupported(),
                properties.isBackchannelLogoutSupported(),
                properties.isBackchannelLogoutSessionSupported());
    }
}
