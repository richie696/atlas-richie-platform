package cn.richie696.component.secret.provider.common;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonHttpSecretTransportTest {
    @Tag("integration")
    @EnabledIfEnvironmentVariable(named = "ATLAS_SECRET_HTTP_TRANSPORT_E2E", matches = "true")
    @Test
    void usesConfiguredWireFieldsAndKeepsEndpointBasePath() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/vendor/secrets/orders", exchange -> {
            byte[] body = "{\"payload\":\"from-provider\",\"revision\":\"7\",\"created\":\"2026-01-01T00:00:00Z\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        server.start();
        try {
            RemoteProviderProperties.Wire wire = new RemoteProviderProperties.Wire();
            wire.setSecretPath("/vendor/secrets/{path}");
            wire.setSecretValueField("payload");
            wire.setSecretVersionField("revision");
            wire.setSecretCreatedAtField("created");
            RemoteProviderProperties.Authentication authentication = new RemoteProviderProperties.Authentication();
            authentication.setType(RemoteProviderProperties.AuthenticationType.NONE);
            try (JsonHttpSecretTransport transport = new JsonHttpSecretTransport(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/api"),
                    authentication, wire, Duration.ofSeconds(1), Duration.ofSeconds(2))) {
                RemoteSecretTransport.RemoteValue value = transport.read("orders", null);
                assertThat(value.value()).isEqualTo("from-provider");
                assertThat(value.version()).isEqualTo("7");
            }
        } finally {
            server.stop(0);
        }
    }

    @Tag("integration")
    @EnabledIfEnvironmentVariable(named = "ATLAS_SECRET_HTTP_TRANSPORT_E2E", matches = "true")
    @Test
    void mapsAuthenticationPermissionTimeoutAndProviderFaultsWithoutFallback() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/fault/401", exchange -> exchange.sendResponseHeaders(401, -1));
        server.createContext("/fault/403", exchange -> exchange.sendResponseHeaders(403, -1));
        server.createContext("/fault/500", exchange -> exchange.sendResponseHeaders(500, -1));
        server.start();
        try {
            RemoteProviderProperties.Wire wire = new RemoteProviderProperties.Wire();
            wire.setSecretPath("/fault/{path}");
            RemoteProviderProperties.Authentication authentication = new RemoteProviderProperties.Authentication();
            authentication.setType(RemoteProviderProperties.AuthenticationType.NONE);
            try (JsonHttpSecretTransport transport = new JsonHttpSecretTransport(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    authentication, wire, Duration.ofSeconds(1), Duration.ofSeconds(1))) {
                assertThatThrownBy(() -> transport.read("401", null)).isInstanceOf(cn.richie696.component.secret.api.exception.SecretException.class)
                        .hasMessageContaining("HTTP 401");
                assertThatThrownBy(() -> transport.read("403", null)).isInstanceOf(cn.richie696.component.secret.api.exception.SecretException.class)
                        .hasMessageContaining("HTTP 403");
                assertThatThrownBy(() -> transport.read("500", null)).isInstanceOf(cn.richie696.component.secret.api.exception.SecretException.class)
                        .hasMessageContaining("HTTP 500");
            }
        } finally { server.stop(0); }
    }
}
