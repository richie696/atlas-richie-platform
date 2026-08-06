package cn.richie696.component.mcp.security.oauth;

import java.net.URI;
import java.util.List;

/**
 * RFC 8414/OIDC Authorization Server Metadata 的最小内部模型。
 */
public record McpAuthorizationServerMetadata(
        URI issuer,
        URI authorizationEndpoint,
        URI tokenEndpoint,
        URI registrationEndpoint,
        List<String> responseTypesSupported,
        List<String> grantTypesSupported,
        List<String> codeChallengeMethodsSupported) {

    public McpAuthorizationServerMetadata {
        issuer = java.util.Objects.requireNonNull(issuer, "issuer");
        responseTypesSupported = responseTypesSupported == null ? List.of() : List.copyOf(responseTypesSupported);
        grantTypesSupported = grantTypesSupported == null ? List.of() : List.copyOf(grantTypesSupported);
        codeChallengeMethodsSupported = codeChallengeMethodsSupported == null
                ? List.of() : List.copyOf(codeChallengeMethodsSupported);
    }
}
