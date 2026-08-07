package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OidcProviderMetadataServiceTest {

    @Test
    void createsDiscoveryMetadataWithOidcFields() {
        var properties = new OidcProperties();
        properties.setIssuer("https://as.example.test");
        properties.setAuthorizationEndpoint("https://as.example.test/oauth2/authorize");
        properties.setTokenEndpoint("https://as.example.test/oauth2/token");
        properties.setUserInfoEndpoint("https://as.example.test/oidc/userinfo");
        properties.setJwksUri("https://as.example.test/oauth2/jwks");

        var metadata = new OidcProviderMetadataService(properties).metadata().asMap();

        assertThat(metadata)
                .containsEntry("issuer", "https://as.example.test")
                .containsEntry("userinfo_endpoint", "https://as.example.test/oidc/userinfo")
                .containsKey("id_token_signing_alg_values_supported")
                .containsKey("scopes_supported")
                .containsEntry("backchannel_logout_supported", true)
                .containsEntry("frontchannel_logout_supported", true);
    }
}
