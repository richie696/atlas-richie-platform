package com.richie.component.oauth.authz.integration;

import com.richie.component.oauth.authz.spi.AuthorizationCodeStore;
import com.richie.component.oauth.authz.support.AbstractOAuthAuthzRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAuthorizationCodeStoreIT extends AbstractOAuthAuthzRedisIntegrationTest {

    @Autowired
    private AuthorizationCodeStore codeStore;

    @Test
    void storeAndLoadAuthorizationCode() {
        codeStore.storeAuthorizationCode(
                "it:code:abc", "client-1",
                "https://example.com/callback",
                "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                "S256",
                List.of("read", "write"),
                "user-001",
                600L);

        Map<String, String> loaded = codeStore.loadAuthorizationCode("it:code:abc");
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("clientId")).isEqualTo("client-1");
        assertThat(loaded.get("redirectUri")).isEqualTo("https://example.com/callback");
        assertThat(loaded.get("codeChallenge")).isEqualTo("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
        assertThat(loaded.get("scopes")).isEqualTo("read write");
        assertThat(loaded.get("userId")).isEqualTo("user-001");
    }

    @Test
    void loadNonExistentCode() {
        Map<String, String> loaded = codeStore.loadAuthorizationCode("it:code:nonexistent");
        assertThat(loaded).isEmpty();
    }

    @Test
    void consumeAuthorizationCode() {
        codeStore.storeAuthorizationCode(
                "it:code:consume", "client-2",
                "https://consume.example.com",
                null, null,
                List.of(), "user-002",
                600L);

        assertThat(codeStore.loadAuthorizationCode("it:code:consume")).isNotNull();

        codeStore.consumeAuthorizationCode("it:code:consume");

        assertThat(codeStore.loadAuthorizationCode("it:code:consume")).isEmpty();
    }

    @Test
    void storeCodeWithEmptyScopes() {
        codeStore.storeAuthorizationCode(
                "it:code:noscopes", "client-3",
                "https://noscope.example.com",
                null, null,
                List.of(), null,
                600L);

        Map<String, String> loaded = codeStore.loadAuthorizationCode("it:code:noscopes");
        assertThat(loaded).isNotNull();
        assertThat(loaded.get("clientId")).isEqualTo("client-3");
        assertThat(loaded.get("scopes")).isEmpty();
    }
}
