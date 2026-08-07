package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;

/** 根据配置生成 OIDC Discovery Metadata，不负责 HTTP 暴露。 */
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
