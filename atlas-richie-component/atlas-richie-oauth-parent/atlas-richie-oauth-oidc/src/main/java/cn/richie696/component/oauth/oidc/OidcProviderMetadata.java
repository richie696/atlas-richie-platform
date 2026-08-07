package cn.richie696.component.oauth.oidc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** OpenID Provider Metadata（与 RFC 8414 OAuth Metadata 兼容的 OIDC 扩展）。 */
public record OidcProviderMetadata(
        String issuer,
        String authorizationEndpoint,
        String tokenEndpoint,
        String deviceAuthorizationEndpoint,
        String userInfoEndpoint,
        String jwksUri,
        String endSessionEndpoint,
        List<String> responseTypesSupported,
        List<String> grantTypesSupported,
        List<String> subjectTypesSupported,
        List<String> scopesSupported,
        List<String> claimsSupported,
        List<String> tokenEndpointAuthMethodsSupported,
        List<String> codeChallengeMethodsSupported,
        List<String> idTokenSigningAlgValuesSupported,
        List<String> responseModesSupported,
        boolean frontchannelLogoutSupported,
        boolean frontchannelLogoutSessionSupported,
        boolean backchannelLogoutSupported,
        boolean backchannelLogoutSessionSupported
) {
    /** 向后兼容旧版 Discovery Metadata 构造方式。 */
    public OidcProviderMetadata(
            String issuer,
            String authorizationEndpoint,
            String tokenEndpoint,
            String userInfoEndpoint,
            String jwksUri,
            String endSessionEndpoint,
            List<String> responseTypesSupported,
            List<String> grantTypesSupported,
            List<String> subjectTypesSupported,
            List<String> scopesSupported,
            List<String> claimsSupported,
            List<String> tokenEndpointAuthMethodsSupported,
            List<String> codeChallengeMethodsSupported,
            List<String> idTokenSigningAlgValuesSupported
    ) {
        this(issuer, authorizationEndpoint, tokenEndpoint, null, userInfoEndpoint, jwksUri,
                endSessionEndpoint, responseTypesSupported, grantTypesSupported,
                subjectTypesSupported, scopesSupported, claimsSupported,
                tokenEndpointAuthMethodsSupported, codeChallengeMethodsSupported,
                idTokenSigningAlgValuesSupported, List.of("query"), false, false, false, false);
    }

    public OidcProviderMetadata {
        responseTypesSupported = copy(responseTypesSupported);
        grantTypesSupported = copy(grantTypesSupported);
        subjectTypesSupported = copy(subjectTypesSupported);
        scopesSupported = copy(scopesSupported);
        claimsSupported = copy(claimsSupported);
        tokenEndpointAuthMethodsSupported = copy(tokenEndpointAuthMethodsSupported);
        codeChallengeMethodsSupported = copy(codeChallengeMethodsSupported);
        idTokenSigningAlgValuesSupported = copy(idTokenSigningAlgValuesSupported);
        responseModesSupported = copy(responseModesSupported);
    }

    /** 返回可直接交给 JSON 序列化器的 snake_case Map。 */
    public Map<String, Object> asMap() {
        Map<String, Object> result = new LinkedHashMap<>();
        put(result, "issuer", issuer);
        put(result, "authorization_endpoint", authorizationEndpoint);
        put(result, "token_endpoint", tokenEndpoint);
        put(result, "device_authorization_endpoint", deviceAuthorizationEndpoint);
        put(result, "userinfo_endpoint", userInfoEndpoint);
        put(result, "jwks_uri", jwksUri);
        put(result, "end_session_endpoint", endSessionEndpoint);
        put(result, "response_types_supported", responseTypesSupported);
        put(result, "grant_types_supported", grantTypesSupported);
        put(result, "subject_types_supported", subjectTypesSupported);
        put(result, "scopes_supported", scopesSupported);
        put(result, "claims_supported", claimsSupported);
        put(result, "token_endpoint_auth_methods_supported", tokenEndpointAuthMethodsSupported);
        put(result, "code_challenge_methods_supported", codeChallengeMethodsSupported);
        put(result, "id_token_signing_alg_values_supported", idTokenSigningAlgValuesSupported);
        put(result, "response_modes_supported", responseModesSupported);
        result.put("frontchannel_logout_supported", frontchannelLogoutSupported);
        result.put("frontchannel_logout_session_supported", frontchannelLogoutSessionSupported);
        result.put("backchannel_logout_supported", backchannelLogoutSupported);
        result.put("backchannel_logout_session_supported", backchannelLogoutSessionSupported);
        return Map.copyOf(result);
    }

    private static List<String> copy(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null && (!(value instanceof List<?> list) || !list.isEmpty())) {
            target.put(key, value);
        }
    }
}
