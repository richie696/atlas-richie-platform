package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class JsonHttpSecretTransportTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void usesOperationSpecificVolcengineFieldsAndEncryptionContext() throws Exception {
        AtomicReference<JsonNode> encryptBody = new AtomicReference<>();
        AtomicReference<JsonNode> decryptBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            if (query != null && query.contains("Action=Encrypt")) {
                encryptBody.set(readJson(exchange));
                respond(exchange, "{\"Result\":{\"CiphertextBlob\":\"d3JhcHBlZA==\"}}");
            } else {
                decryptBody.set(readJson(exchange));
                respond(exchange, "{\"Result\":{\"Plaintext\":\"cGxhaW4=\"}}");
            }
        });
        server.start();
        try {
            RemoteProviderProperties properties = new RemoteProviderProperties();
            properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
            OfficialWireProfiles.apply("volcengine", properties);
            JsonHttpSecretTransport transport = new JsonHttpSecretTransport(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    properties.getAuthentication(), properties.getWire(),
                    Map.of("namespace", "atlas", "region", "cn-beijing"),
                    Duration.ofSeconds(1), Duration.ofSeconds(1));
            CryptoContext context = new CryptoContext("application=test".getBytes(StandardCharsets.UTF_8),
                    Map.of("atlas.secret.key", "oauth-signing"));

            assertThat(transport.wrap("master", "data-key".getBytes(StandardCharsets.UTF_8), context))
                    .isEqualTo("wrapped".getBytes(StandardCharsets.UTF_8));
            assertThat(transport.unwrap("master", "wrapped".getBytes(StandardCharsets.UTF_8), context))
                    .isEqualTo("plain".getBytes(StandardCharsets.UTF_8));

            assertThat(encryptBody.get().path("Plaintext").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
            assertThat(encryptBody.get().has("CiphertextBlob")).isFalse();
            assertThat(encryptBody.get().path("EncryptionContext").path("atlas.secret.key").asText())
                    .isEqualTo("oauth-signing");
            assertThat(encryptBody.get().path("EncryptionContext")
                    .path("atlas.secret.associated-data-sha256").asText()).hasSize(64);
            assertThat(decryptBody.get().path("CiphertextBlob").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString("wrapped".getBytes(StandardCharsets.UTF_8)));
            assertThat(decryptBody.get().has("Plaintext")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void doesNotAppendDuplicateVersionQueryWhenTemplateContainsVersion() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            respond(exchange, "{\"payload\":{\"data\":\"dmFsdWU=\"},\"name\":\"7\"}");
        });
        server.start();
        try {
            RemoteProviderProperties properties = new RemoteProviderProperties();
            properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
            OfficialWireProfiles.apply("gcp", properties);
            JsonHttpSecretTransport transport = new JsonHttpSecretTransport(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    properties.getAuthentication(), properties.getWire(),
                    Map.of("projectId", "atlas", "region", "global", "namespace", "main"),
                    Duration.ofSeconds(1), Duration.ofSeconds(1));

            RemoteSecretTransport.RemoteValue value = transport.read("oauth", SecretVersionSelector.version("7"));

            assertThat(value.value()).isEqualTo("value");
            assertThat(requestUri.get().getRawPath())
                    .isEqualTo("/v1/projects/atlas/secrets/oauth/versions/7:access");
            assertThat(requestUri.get().getRawQuery()).isNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void azureUsesVersionedKeyApiVersionAlgorithmAndBase64Url() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            requestBody.set(readJson(exchange));
            respond(exchange, "{\"value\":\"d3JhcHBlZA\"}");
        });
        server.start();
        try {
            RemoteProviderProperties properties = properties("azure");
            JsonHttpSecretTransport transport = transport(server, properties,
                    Map.of("apiVersion", properties.getApiVersion()));

            assertThat(transport.wrap("master-key/0001", "data-key".getBytes(StandardCharsets.UTF_8),
                    CryptoContext.empty())).isEqualTo("wrapped".getBytes(StandardCharsets.UTF_8));

            assertThat(requestUri.get().getRawPath()).isEqualTo("/keys/master-key/0001/wrapkey");
            assertThat(requestUri.get().getRawQuery()).isEqualTo("api-version=2025-07-01");
            assertThat(requestBody.get().path("alg").asText()).isEqualTo("RSA-OAEP-256");
            assertThat(requestBody.get().path("value").asText())
                    .isEqualTo(Base64.getUrlEncoder().withoutPadding()
                            .encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
            assertThat(requestBody.get().has("aad")).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void tencentUsesSeparateSsmAndKmsContractsWithOperationSpecificHeaders() throws Exception {
        AtomicReference<JsonNode> secretBody = new AtomicReference<>();
        AtomicReference<String> secretAction = new AtomicReference<>();
        AtomicReference<JsonNode> kmsBody = new AtomicReference<>();
        AtomicReference<String> kmsAction = new AtomicReference<>();
        AtomicReference<String> kmsVersion = new AtomicReference<>();
        HttpServer secretServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        secretServer.createContext("/", exchange -> {
            secretBody.set(readJson(exchange));
            secretAction.set(exchange.getRequestHeaders().getFirst("X-TC-Action"));
            respond(exchange, "{\"Response\":{\"SecretString\":\"secret-value\",\"VersionId\":\"v1\"}}");
        });
        HttpServer kmsServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        kmsServer.createContext("/", exchange -> {
            kmsBody.set(readJson(exchange));
            kmsAction.set(exchange.getRequestHeaders().getFirst("X-TC-Action"));
            kmsVersion.set(exchange.getRequestHeaders().getFirst("X-TC-Version"));
            respond(exchange, "{\"Response\":{\"CiphertextBlob\":\"d3JhcHBlZA==\"}}");
        });
        secretServer.start();
        kmsServer.start();
        try {
            RemoteProviderProperties properties = properties("tencent");
            properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.ACCESS_KEY);
            properties.getAuthentication().setAccessKeyId("test-id");
            properties.getAuthentication().setAccessKeySecret("test-secret".toCharArray());
            OfficialWireProfiles.apply("tencent", properties);
            JsonHttpSecretTransport transport = transport(secretServer, kmsServer, properties,
                    Map.of("region", "ap-guangzhou"));

            RemoteSecretTransport.RemoteValue secret = transport.read("database", SecretVersionSelector.latest());
            assertThat(secret.value()).isEqualTo("secret-value");
            assertThat(secretBody.get().path("SecretName").asText()).isEqualTo("database");
            assertThat(secretBody.get().path("VersionId").asText()).isEqualTo("SSM_Current");
            assertThat(secretAction).hasValue("GetSecretValue");

            assertThat(transport.wrap("cmk-id", "data-key".getBytes(StandardCharsets.UTF_8),
                    CryptoContext.empty())).isEqualTo("wrapped".getBytes(StandardCharsets.UTF_8));
            assertThat(kmsAction).hasValue("Encrypt");
            assertThat(kmsVersion).hasValue("2019-01-18");
            assertThat(kmsBody.get().path("KeyId").asText()).isEqualTo("cmk-id");
            assertThat(kmsBody.get().path("Plaintext").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
        } finally {
            secretServer.stop(0);
            kmsServer.stop(0);
        }
    }

    @Test
    void huaweiUsesOfficialCsmsAndKmsShapes() throws Exception {
        AtomicReference<URI> lastUri = new AtomicReference<>();
        AtomicReference<JsonNode> lastBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            lastUri.set(exchange.getRequestURI());
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, "{\"version\":{\"version_metadata\":{\"id\":\"v1\",\"create_time\":1700000000000},\"secret_string\":\"secret-value\"}}");
            } else {
                lastBody.set(readJson(exchange));
                respond(exchange, "{\"cipher_text\":\"d3JhcHBlZA==\"}");
            }
        });
        server.start();
        try {
            RemoteProviderProperties properties = properties("huawei");
            JsonHttpSecretTransport transport = transport(server, properties, Map.of("projectId", "project-1"));

            RemoteSecretTransport.RemoteValue secret = transport.read("database", SecretVersionSelector.latest());
            assertThat(secret.value()).isEqualTo("secret-value");
            assertThat(secret.version()).isEqualTo("v1");
            assertThat(lastUri.get().getRawPath())
                    .isEqualTo("/v1/project-1/secrets/database/versions/latest");

            assertThat(transport.wrap("cmk-id", "data-key".getBytes(StandardCharsets.UTF_8),
                    CryptoContext.empty())).isEqualTo("wrapped".getBytes(StandardCharsets.UTF_8));
            assertThat(lastUri.get().getRawPath()).isEqualTo("/v1.0/project-1/kms/encrypt-data");
            assertThat(lastBody.get().path("key_id").asText()).isEqualTo("cmk-id");
            assertThat(lastBody.get().path("plain_text").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void baiduUsesActionApiAndOfficialFields() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            requestBody.set(readJson(exchange));
            respond(exchange, "{\"ciphertext\":\"d3JhcHBlZA==\"}");
        });
        server.start();
        try {
            RemoteProviderProperties properties = properties("baidu");
            JsonHttpSecretTransport transport = transport(server, properties, Map.of());

            assertThat(transport.wrap("cmk-id", "data-key".getBytes(StandardCharsets.UTF_8),
                    CryptoContext.empty())).isEqualTo("wrapped".getBytes(StandardCharsets.UTF_8));
            assertThat(requestUri.get().getRawPath()).isEqualTo("/");
            assertThat(requestUri.get().getRawQuery()).isEqualTo("action=Encrypt");
            assertThat(requestBody.get().path("keyId").asText()).isEqualTo("cmk-id");
            assertThat(requestBody.get().path("plaintext").asText())
                    .isEqualTo(Base64.getEncoder().encodeToString("data-key".getBytes(StandardCharsets.UTF_8)));
            assertThat(requestBody.get().path("algorithmMode").asText()).isEqualTo("GCM");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void pathTemplateUsesRfc3986EncodingInsteadOfFormEncoding() throws Exception {
        AtomicReference<URI> requestUri = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            respond(exchange, "{\"value\":\"ok\",\"version\":\"v1\"}");
        });
        server.start();
        try {
            RemoteProviderProperties properties = properties("unknown");
            JsonHttpSecretTransport transport = transport(server, properties, Map.of());

            transport.read("name with space", SecretVersionSelector.latest());

            assertThat(requestUri.get().getRawPath()).isEqualTo("/secrets/name%20with%20space");
        } finally {
            server.stop(0);
        }
    }

    private RemoteProviderProperties properties(String provider) {
        RemoteProviderProperties properties = new RemoteProviderProperties();
        properties.getAuthentication().setType(RemoteProviderProperties.AuthenticationType.NONE);
        OfficialWireProfiles.apply(provider, properties);
        return properties;
    }

    private JsonHttpSecretTransport transport(
            HttpServer server,
            RemoteProviderProperties properties,
            Map<String, String> variables) {
        return transport(server, server, properties, variables);
    }

    private JsonHttpSecretTransport transport(
            HttpServer secretServer,
            HttpServer kmsServer,
            RemoteProviderProperties properties,
            Map<String, String> variables) {
        return new JsonHttpSecretTransport(
                URI.create("http://127.0.0.1:" + secretServer.getAddress().getPort()),
                URI.create("http://127.0.0.1:" + kmsServer.getAddress().getPort()),
                properties.getAuthentication(), properties.getWire(), variables,
                new RemoteProviderProperties.Tls(), new RemoteProviderProperties.Proxy(),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 1);
    }

    private JsonNode readJson(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        try {
            return mapper.readTree(body);
        } finally {
            java.util.Arrays.fill(body, (byte) 0);
        }
    }

    private void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        try {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            java.util.Arrays.fill(body, (byte) 0);
            exchange.close();
        }
    }
}
