package cn.richie696.component.oauth.test;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.core.ClientRegistry;
import cn.richie696.component.oauth.core.TokenEndpoint;
import cn.richie696.component.oauth.core.config.OAuth2Properties;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.model.TokenResponse;
import cn.richie696.component.oauth.core.spi.TokenStore;
import cn.richie696.component.oauth.core.support.CacheBackedTokenStore;
import cn.richie696.component.oauth.core.support.HmacAccessTokenSigner;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 最小 RFC 6749/RFC 8707/refresh rotation 可执行一致性用例。 */
class OAuthCoreConformanceTest {

    @Test
    void clientCredentialsResourceAudienceAndRefreshReuseAreEnforced() {
        ClientConfig client = ClientConfig.builder()
                .clientId("client-1").clientSecret("secret").clientName("test")
                .enabled(true).scopes(List.of("read"))
                .grantTypes(List.of("client_credentials", "refresh_token"))
                .resource("https://api.example")
                .tokenValidDuration(1).refreshTokenValidDuration(1).build();
        ClientRegistry registry = new ClientRegistry(new InMemoryClientRepository(client));
        OAuth2Properties properties = new OAuth2Properties();
        properties.setTokenSecret("test-secret-key-32chars-long!!!!");
        properties.setEnableDailyIssueLimit(false);
        TokenStore store = new CacheBackedTokenStore(new InMemoryOAuthCache());
        TokenEndpoint endpoint = new TokenEndpoint(store, registry, properties,
                new HmacAccessTokenSigner(properties), new InMemoryOAuthCache());

        TokenResponse first = endpoint.generateToken("client-1", "secret", "127.0.0.1",
                "https://api.example");
        assertEquals("https://api.example", JWT.decode(first.getAccessToken()).getAudience().getFirst());
        OAuthTestAssertions.assertValidToken(first);

        TokenResponse refreshed = endpoint.refreshToken(first.getRefreshToken(), "127.0.0.1");
        assertTrue(refreshed.getRefreshToken() != null && !refreshed.getRefreshToken().isBlank());
        assertThrows(RuntimeException.class,
                () -> endpoint.refreshToken(first.getRefreshToken(), "127.0.0.1"));
    }

    private record InMemoryClientRepository(ClientConfig client) implements cn.richie696.component.oauth.core.spi.ClientRepository {
        @Override public ClientConfig find(String clientId) {
            return client.getClientId().equals(clientId) ? client : null;
        }
        @Override public void save(ClientConfig ignored) { }
    }
}
