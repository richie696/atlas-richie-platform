package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.contract.model.OAuthAuthorizationRequest;
import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class OidcAuthorizationRequestValidatorTest {

    @Test
    void acceptsOpenIdRequestWithNonce() {
        var request = request("nonce-1");

        assertThat(new OidcAuthorizationRequestValidator(new OidcProperties()).validate(request))
                .isSameAs(request);
    }

    @Test
    void rejectsOpenIdRequestWithoutNonce() {
        assertThatThrownBy(() -> new OidcAuthorizationRequestValidator(new OidcProperties())
                .validate(request(null)))
                .hasMessageContaining("nonce");
    }

    private OAuthAuthorizationRequest request(String nonce) {
        return new OAuthAuthorizationRequest("client-1", "https://client.example/callback", "code",
                List.of("openid", "profile"), "state", null, "challenge", "S256", nonce);
    }
}
