package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.resource.DefaultJwtTokenVerifier;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OAuthTestKeyMaterialTest {

    @Test
    void signsAndVerifiesResourceServerToken() {
        var keys = OAuthTestKeyMaterial.generate("it-kid");
        String token = keys.accessToken(
                "https://as.example.test", "resource-api", "client-1", "user-1", java.util.List.of("read"));

        var principal = new DefaultJwtTokenVerifier(
                "https://as.example.test", "resource-api", keys.jwkSource()).verify(token);

        assertThat(principal.clientId()).isEqualTo("client-1");
        assertThat(principal.subject()).isEqualTo("user-1");
        assertThat(principal.scopes()).containsExactly("read");
        assertThat(keys.jwksJson()).contains("\"kid\":\"it-kid\"");
    }
}
