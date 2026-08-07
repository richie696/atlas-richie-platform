package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Token endpoint 的标准请求模型。 */
public record OAuthTokenRequest(
        @JsonProperty("grant_type")
        String grantType,
        @JsonProperty("client_id")
        String clientId,
        @JsonProperty("client_secret")
        String clientSecret,
        String code,
        @JsonProperty("code_verifier")
        String codeVerifier,
        @JsonProperty("redirect_uri")
        String redirectUri,
        @JsonProperty("refresh_token")
        String refreshToken,
        String scope,
        String resource,
        @JsonProperty("device_code")
        String deviceCode
) {
    public OAuthTokenRequest(String grantType, String clientId, String clientSecret,
                             String code, String codeVerifier, String redirectUri,
                             String refreshToken, String scope, String resource) {
        this(grantType, clientId, clientSecret, code, codeVerifier, redirectUri,
                refreshToken, scope, resource, null);
    }
}
