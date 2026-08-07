package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.core.model.ClientConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class OidcLogoutValidatorTest {

    @Test
    void acceptsRegisteredPostLogoutRedirectUri() {
        var client = ClientConfig.builder().clientId("client-1")
                .redirectUris(List.of("https://client.example/logout/callback")).build();
        var request = new OidcLogoutRequest("id-token", null, "client-1",
                "https://client.example/logout/callback", "state-1");

        assertThat(new OidcLogoutValidator().validate(request, client)).isSameAs(request);
    }

    @Test
    void rejectsUnregisteredPostLogoutRedirectUri() {
        var client = ClientConfig.builder().clientId("client-1").redirectUris(List.of()).build();
        var request = new OidcLogoutRequest("id-token", null, "client-1",
                "https://evil.example/callback", null);

        assertThatThrownBy(() -> new OidcLogoutValidator().validate(request, client))
                .hasMessageContaining("未注册");
    }
}
