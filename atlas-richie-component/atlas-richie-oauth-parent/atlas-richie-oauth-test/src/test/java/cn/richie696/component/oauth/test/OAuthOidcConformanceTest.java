package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.oidc.OidcAuthorizationRequestValidator;
import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** OIDC Basic 的最小契约，以及当前明确拒绝 Hybrid 的边界用例。 */
class OAuthOidcConformanceTest {

    @Test
    void oidcBasicRequiresOpenIdScopeAndNonce() {
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                "client-1", "https://client.example/callback", "code",
                List.of("openid", "profile"), "state-1", null,
                "challenge", "S256", "nonce-1");

        assertSame(request, new OidcAuthorizationRequestValidator(new OidcProperties()).validate(request));
    }

    @Test
    void oidcHybridIsRejectedUntilTheResponseModeContractExists() {
        OAuthAuthorizationRequest request = new OAuthAuthorizationRequest(
                "client-1", "https://client.example/callback", "code id_token",
                List.of("openid"), "state-1", null,
                "challenge", "S256", "nonce-1");

        assertThrows(RuntimeException.class,
                () -> new OidcAuthorizationRequestValidator(new OidcProperties()).validate(request));
    }
}
