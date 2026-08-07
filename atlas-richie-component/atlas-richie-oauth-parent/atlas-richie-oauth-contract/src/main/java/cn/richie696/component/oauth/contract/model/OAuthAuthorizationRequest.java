package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 与 Web 框架无关的授权请求模型。 */
public record OAuthAuthorizationRequest(
        String clientId,
        String redirectUri,
        String responseType,
        List<String> scopes,
        String state,
        String resource,
        @JsonProperty("code_challenge")
        String codeChallenge,
        @JsonProperty("code_challenge_method")
        String codeChallengeMethod,
        String nonce,
        @JsonProperty("response_mode")
        String responseMode
) {
    /** 向后兼容原有 OAuth 请求构造方式。 */
    public OAuthAuthorizationRequest(
            String clientId,
            String redirectUri,
            String responseType,
            List<String> scopes,
            String state,
            String resource,
            String codeChallenge,
            String codeChallengeMethod
    ) {
        this(clientId, redirectUri, responseType, scopes, state, resource,
                codeChallenge, codeChallengeMethod, null, null);
    }

    /** 向后兼容已包含 nonce 的旧版构造方式。 */
    public OAuthAuthorizationRequest(
            String clientId,
            String redirectUri,
            String responseType,
            List<String> scopes,
            String state,
            String resource,
            String codeChallenge,
            String codeChallengeMethod,
            String nonce
    ) {
        this(clientId, redirectUri, responseType, scopes, state, resource,
                codeChallenge, codeChallengeMethod, nonce, null);
    }

    public OAuthAuthorizationRequest {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
    }

    /**
     * RFC 8707 资源指示器的兼容视图。旧版本 API 保留单值 resource；
     * 服务适配层可以在未来通过重复参数构造多个领域请求。
     */
    public List<String> resourceIndicators() {
        return resource == null || resource.isBlank() ? List.of() : List.of(resource);
    }
}
