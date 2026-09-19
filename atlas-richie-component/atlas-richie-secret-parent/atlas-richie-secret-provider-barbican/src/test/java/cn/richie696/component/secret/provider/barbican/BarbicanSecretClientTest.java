package cn.richie696.component.secret.provider.barbican;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BarbicanSecretClientTest {
    private HttpServer server;
    private BarbicanSecretClient client;
    private volatile int metadataStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/secrets", this::handle);
        server.start();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.barbican.endpoint", endpoint())
                .withProperty("platform.component.secret.barbican.authentication.token", "test-token")
                .withProperty("platform.component.secret.barbican.secrets.database.id", "physical-secret");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getResilience().setMaxAttempts(1);
        client = (BarbicanSecretClient) new BarbicanSecretBootstrapProviderFactory().create(
                bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader(),
                        "barbican", "barbican", "platform.component.secret.barbican"));
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsPayloadLoadsBootstrapValuesAndExposesMetadata() {
        try (var value = client.read(SecretReference.latest("database"))) {
            assertThat(value.copyBytes()).containsExactly("barbican-secret".getBytes(StandardCharsets.UTF_8));
        }
        var result = client.load(new SecretBootstrapRequest("orders", "prod", java.util.List.of("database"), java.util.List.of()));
        assertThat(result.providerId()).isEqualTo("barbican");
        assertThat(result.values()).containsEntry("database", "barbican-secret");
        assertThat(client.metadata(SecretReference.latest("database")).attributes())
                .containsEntry("provider", "barbican");
        assertThat(client.descriptor().capabilities()).containsExactlyInAnyOrder(
                SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING);
        assertThat(client.secretBackend()).contains(client);
        assertThat(client.keyWrappingBackend()).isEmpty();
        assertThat(client.configurationHash()).hasSize(64);
    }

    @Test
    void mapsUnauthorizedResponsesAndRejectsUseAfterClose() {
        metadataStatus = 401;
        assertThatThrownBy(() -> client.read(SecretReference.latest("database")))
                .isInstanceOfSatisfying(SecretException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo("SEC-AUTH-001"));
        client.close();
        assertThatThrownBy(() -> client.read(SecretReference.latest("database")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    @Test
    void missingPropertySourceSecretCanBeIgnoredByLocalPolicy() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.barbican.endpoint", endpoint())
                .withProperty("platform.component.secret.barbican.authentication.token", "test-token");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getResilience().setMaxAttempts(1);
        bootstrap.getPropertySource().setMissingPolicy(BootstrapSecretProperties.MissingPolicy.LOCAL);
        try (var local = (BarbicanSecretClient) new BarbicanSecretBootstrapProviderFactory().create(
                bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader(),
                        "barbican", "barbican", "platform.component.secret.barbican"))) {
            assertThat(local.load(new SecretBootstrapRequest("orders", "prod", java.util.List.of("missing"), java.util.List.of()))
                    .values()).isEmpty();
        }
    }

    private String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        int status = metadataStatus;
        byte[] body = new byte[0];
        if (status == 200 && path.endsWith("/physical-secret")) {
            body = "{\"created\":\"2026-09-19T00:00:00Z\",\"updated\":\"2026-09-19T01:00:00Z\",\"expiration\":null,\"status\":\"active\"}".getBytes(StandardCharsets.UTF_8);
        } else if (status == 200 && path.endsWith("/physical-secret/payload")) {
            exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
            body = "barbican-secret".getBytes(StandardCharsets.UTF_8);
        } else if (status == 200) {
            status = 404;
        }
        exchange.sendResponseHeaders(status, body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
        Arrays.fill(body, (byte) 0);
    }
}
