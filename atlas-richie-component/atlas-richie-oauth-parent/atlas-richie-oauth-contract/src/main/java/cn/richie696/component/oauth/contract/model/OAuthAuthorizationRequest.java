package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * authorization_code grant 的请求参数 record, 承载 clientId、redirectUri、responseType、scopes、state、resource、PKCE 三元组以及 OIDC 专属的 nonce 与 responseMode。
 * <p>
 * 处于契约层授权端点的入参一环, 是 HTTP 适配层把 query / form 反序列化后的最终业务对象, 上游 core / authz 中的授权服务直接消费这份 record 完成请求规范化。
 * 解决"Web 框架绑定让授权请求难以在非 Web 场景或 OIDC 流程下复用"的问题, 让契约层在框架之外仍保持完整的字段表达, 同时通过旧构造器兼容历史签名。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
