package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OidcUserInfoServiceTest {

    @Test
    void onlyReturnsClaimsGrantedByRequestedScopes() {
        var properties = new OidcProperties();
        var service = new OidcUserInfoService(properties, subject -> Map.of(
                "name", "Alice",
                "email", "alice@example.test",
                "email_verified", true,
                "phone_number", "+8613800000000"));

        var profile = service.load("user-1", java.util.List.of("openid", "profile"));
        var email = service.load("user-1", java.util.List.of("openid", "email"));

        assertThat(profile.asMap()).containsEntry("sub", "user-1")
                .containsEntry("name", "Alice")
                .doesNotContainKey("email");
        assertThat(email.asMap()).containsEntry("sub", "user-1")
                .containsEntry("email", "alice@example.test")
                .doesNotContainKey("name");
    }
}
