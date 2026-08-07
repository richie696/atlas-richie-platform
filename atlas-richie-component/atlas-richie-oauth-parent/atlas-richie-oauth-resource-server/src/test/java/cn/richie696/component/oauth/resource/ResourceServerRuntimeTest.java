package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthPrincipal;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ResourceServerRuntimeTest {

    @Test
    void httpJwkSourceFetchesAndCachesRsaKey() throws Exception {
        var keyPair = java.security.KeyPairGenerator.getInstance("RSA");
        keyPair.initialize(2048);
        var pair = keyPair.generateKeyPair();
        var publicKey = (java.security.interfaces.RSAPublicKey) pair.getPublic();
        String n = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(unsigned(publicKey.getModulus().toByteArray()));
        String e = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(unsigned(publicKey.getPublicExponent().toByteArray()));
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/jwks", exchange -> {
            requests.incrementAndGet();
            byte[] body = ("{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"" + n
                    + "\",\"e\":\"" + e + "\"}]} ").getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            var source = new HttpJwkSource(URI.create("http://localhost:" + server.getAddress().getPort() + "/jwks"),
                    new InMemoryOAuthCache(), Duration.ofSeconds(2), Duration.ofMinutes(1));
            assertThat(source.find("k1")).isNotNull();
            assertThat(source.find("k1")).isNotNull();
            assertThat(requests).hasValue(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cachesIntrospectionAndFallsBackToIntrospection() {
        var cache = new InMemoryOAuthCache();
        IntrospectionClient delegate = mock(IntrospectionClient.class);
        var response = new OAuthIntrospectionResponse(true, "client-1", "Bearer", "read",
                "user-1", "iss", "aud", System.currentTimeMillis() / 1000 + 60,
                System.currentTimeMillis() / 1000, "tid", java.util.Map.of());
        when(delegate.introspect("opaque")).thenReturn(response);
        var client = new CachingIntrospectionClient(cache, delegate, 1_000);
        assertThat(client.introspect("opaque")).isEqualTo(response);
        assertThat(client.introspect("opaque")).isEqualTo(response);
        verify(delegate, times(1)).introspect("opaque");

        JwtTokenVerifier verifier = token -> { throw new ResourceServerException("not jwt"); };
        var authenticator = new ResourceServerAuthenticator(verifier, client, true);
        OAuthPrincipal principal = authenticator.authenticate("opaque");
        assertThat(principal.subject()).isEqualTo("user-1");
        assertThatThrownBy(() -> client.introspect(" ")).isInstanceOf(ResourceServerException.class);
    }

    private byte[] unsigned(byte[] value) {
        return value.length > 1 && value[0] == 0 ? java.util.Arrays.copyOfRange(value, 1, value.length) : value;
    }
}
