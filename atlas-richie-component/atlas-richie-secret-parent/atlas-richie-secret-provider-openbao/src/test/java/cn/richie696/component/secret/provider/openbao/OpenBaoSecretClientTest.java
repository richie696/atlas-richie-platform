package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenBaoSecretClientTest {
    @Test
    void exercisesSecretTransitAndSigningContractsOverLocalHttp() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", OpenBaoSecretClientTest::respond);
        server.start();
        try {
            OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
            properties.setEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.getAuthentication().setToken("token".toCharArray());
            OpenBaoSecretProperties.SecretMapping mapping = new OpenBaoSecretProperties.SecretMapping();
            mapping.setPath("runtime/database");
            mapping.setField("password");
            properties.setSecrets(Map.of("database", mapping));
            properties.getTransit().setKeyBindings(Map.of("envelope", "envelope-key", "signing", "signing-key"));

            BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
            bootstrap.getPropertySource().setApplication("orders");
            bootstrap.getPropertySource().setEnvironment("prod");
            OpenBaoSecretClient client = new OpenBaoSecretClient("openbao", "hash", properties, bootstrap);

            var loaded = client.load(new SecretBootstrapRequest("orders", "prod", List.of("bundle"), List.of()));
            try (var value = client.read(SecretReference.latest("database"))) {
                assertThat(value.copyChars()).containsExactly("s3cret".toCharArray());
            }
            var metadata = client.metadata(SecretReference.latest("database"));
            var wrapped = client.wrap(KeyReference.envelopeEncryption("envelope"),
                    "data-key".getBytes(StandardCharsets.UTF_8), CryptoContext.empty());
            var unwrapped = client.unwrap(KeyReference.envelopeEncryption("envelope"), wrapped, CryptoContext.empty());
            KeyReference signing = new KeyReference("signing", "current", KeyPurpose.SIGNING);
            SignatureValue signature = client.sign(signing, "payload".getBytes(StandardCharsets.UTF_8), CryptoContext.empty());

            assertThat(client.verify(signing, "payload".getBytes(StandardCharsets.UTF_8), signature, CryptoContext.empty())).isTrue();
            assertThat(new String(unwrapped, StandardCharsets.UTF_8)).isEqualTo("data-key");
            assertThat(loaded.values()).containsEntry("password", "s3cret");
            assertThat(metadata.version()).isEqualTo("2");
            assertThat(client.descriptor().providerType()).isEqualTo("openbao");
            assertThat(client.secretBackend()).contains(client);
            assertThat(client.keyWrappingBackend()).contains(client);
            assertThat(client.signingBackend()).contains(client);
            client.close();
            assertThatThrownBy(() -> client.read(SecretReference.latest("database")))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsMissingHttpSecretAndRejectsInvalidSigningKey() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            OpenBaoSecretProperties properties = new OpenBaoSecretProperties();
            properties.setEndpoint(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.getAuthentication().setToken("token".toCharArray());
            BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
            try (OpenBaoSecretClient client = new OpenBaoSecretClient("openbao", "hash", properties, bootstrap)) {
                assertThatThrownBy(() -> client.read(SecretReference.latest("missing")))
                        .isInstanceOf(SecretException.class).hasMessageContaining("missing");
                assertThatThrownBy(() -> client.sign(KeyReference.envelopeEncryption("key"),
                        new byte[]{1}, CryptoContext.empty())).isInstanceOf(RuntimeException.class);
            }
        } finally {
            server.stop(0);
        }
    }

    private static void respond(HttpExchange exchange) throws java.io.IOException {
        String path = exchange.getRequestURI().getPath();
        String body;
        if ("POST".equals(exchange.getRequestMethod()) && path.contains("/encrypt/")) {
            body = "{\"data\":{\"ciphertext\":\"vault:v1:cipher\"}}";
        } else if ("POST".equals(exchange.getRequestMethod()) && path.contains("/decrypt/")) {
            body = "{\"data\":{\"plaintext\":\"ZGF0YS1rZXk=\"}}";
        } else if ("POST".equals(exchange.getRequestMethod()) && path.contains("/sign/")) {
            body = "{\"data\":{\"signature\":\"signature\"}}";
        } else if ("POST".equals(exchange.getRequestMethod()) && path.contains("/verify/")) {
            body = "{\"data\":{\"valid\":true}}";
        } else {
            body = "{\"data\":{\"data\":{\"password\":\"s3cret\"},\"metadata\":{\"version\":2,\"created_time\":\"2026-01-01T00:00:00Z\"}}}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
