package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Strict JSON transport used by providers whose vendor SDK is optional. The
 * endpoint is a vendor REST endpoint or a local contract-test facade; no
 * plaintext fallback is ever performed.
 */
public final class JsonHttpSecretTransport implements RemoteSecretTransport {
    private final URI endpoint;
    private final RemoteProviderProperties.Authentication authentication;
    private final RemoteProviderProperties.Wire wire;
    private final Map<String, String> variables;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   Duration connectTimeout,
                                   Duration readTimeout) {
        this(endpoint, authentication, new RemoteProviderProperties.Wire(), connectTimeout, readTimeout);
    }

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Duration connectTimeout,
                                   Duration readTimeout) {
        this(endpoint, authentication, wire, Map.of(), connectTimeout, readTimeout);
    }

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Map<String, String> variables,
                                   Duration connectTimeout,
                                   Duration readTimeout) {
        this(endpoint, authentication, wire, variables, new RemoteProviderProperties.Tls(),
                new RemoteProviderProperties.Proxy(), connectTimeout, readTimeout);
    }

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Map<String, String> variables,
                                   RemoteProviderProperties.Tls tls,
                                   RemoteProviderProperties.Proxy proxy,
                                   Duration connectTimeout,
                                   Duration readTimeout) {
        if (endpoint == null || endpoint.getHost() == null) {
            throw new IllegalArgumentException("provider endpoint must include a host");
        }
        this.endpoint = endpoint.toString().endsWith("/")
                ? URI.create(endpoint.toString().substring(0, endpoint.toString().length() - 1)) : endpoint;
        this.authentication = authentication;
        this.wire = wire == null ? new RemoteProviderProperties.Wire() : wire;
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
        this.timeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        this.client = RemoteHttpClientFactory.create(connectTimeout, tls, proxy);
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        String version = selector == null || selector.type() == SecretVersionSelector.Type.LATEST
                ? "latest" : selector.value();
        Map<String, String> pathVariables = new LinkedHashMap<>(variables);
        pathVariables.put("path", path);
        pathVariables.put("version", version);
        String suffix = template(wire.getSecretPath(), pathVariables);
        if (selector != null && selector.type() != SecretVersionSelector.Type.LATEST) {
            suffix += "?version=" + encode(selector.value());
        }
        JsonNode node = send("GET", suffix, null);
        if (node == null) return null;
        JsonNode value = fieldNode(node, wire.getSecretValueField());
        if (value == null) value = node.get("data");
        if (value == null || value.isNull()) {
            throw new SecretException("SEC-STORE-001", "Remote Secret response has no value");
        }
        Object mapped = decodeSecretValue(value);
        return new RemoteValue(mapped, text(node, wire.getSecretVersionField(), "latest"),
                instant(fieldNode(node, wire.getSecretCreatedAtField())), attributes(node));
    }

    @Override
    public byte[] wrap(String key, byte[] plaintext, CryptoContext context) {
        if (plaintext == null || plaintext.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        }
        try {
            Map<String, String> pathVariables = new LinkedHashMap<>(variables);
            pathVariables.put("key", key);
            JsonNode node = send("POST", template(wire.getWrapPath(), pathVariables), cryptoBody(key, plaintext, context));
            String value = text(node, wire.getWrappedKeyField(), null);
            if (value == null) throw new SecretCryptoException("SEC-CRYPTO-001", "Remote KMS returned no wrapped key");
            return Base64.getDecoder().decode(value);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Remote KMS wrap failed", exception);
        }
    }

    @Override
    public byte[] unwrap(String key, byte[] wrapped, CryptoContext context) {
        if (wrapped == null || wrapped.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped data key must not be empty");
        }
        try {
            Map<String, String> pathVariables = new LinkedHashMap<>(variables);
            pathVariables.put("key", key);
            JsonNode node = send("POST", template(wire.getUnwrapPath(), pathVariables), cryptoBody(key, wrapped, context));
            String value = text(node, wire.getPlaintextField(), null);
            if (value == null) throw new SecretCryptoException("SEC-CRYPTO-002", "Remote KMS returned no plaintext");
            return Base64.getDecoder().decode(value);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Remote KMS unwrap failed", exception);
        }
    }

    private Map<String, Object> cryptoBody(String key, byte[] value, CryptoContext context) {
        byte[] aad = context == null ? new byte[0] : context.associatedData();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(wire.getRequestValueField(), Base64.getEncoder().encodeToString(value));
            body.put(wire.getRequestAadField(), Base64.getEncoder().encodeToString(aad));
            if (wire.getRequestKeyField() != null && !wire.getRequestKeyField().isBlank()) {
                body.put(wire.getRequestKeyField(), key);
            }
            if (context != null && !context.attributes().isEmpty()) {
                body.put("context", Map.copyOf(context.attributes()));
            }
            return body;
        } finally {
            java.util.Arrays.fill(aad, (byte) 0);
        }
    }

    private JsonNode send(String method, String suffix, Object body) {
        byte[] requestBody = null;
        byte[] responseBody = null;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(resolve(suffix))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .header("User-Agent", "atlas-richie-secret");
            authenticate(builder);
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                requestBody = mapper.writeValueAsBytes(body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            }
            RemoteRequestSigner.apply(builder, resolve(suffix), method,
                    requestBody == null ? new byte[0] : requestBody, authentication, variables);
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            responseBody = response.body();
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String code = switch (response.statusCode()) {
                    case 401 -> "SEC-AUTH-001";
                    case 403 -> "SEC-AUTHZ-001";
                    case 409, 410 -> "SEC-PROVIDER-002";
                    default -> "SEC-PROVIDER-001";
                };
                throw new SecretException(code, "Remote Secret provider returned HTTP " + response.statusCode());
            }
            return mapper.readTree(responseBody);
        } catch (SecretException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SecretException("SEC-PROVIDER-001", "Remote Secret provider request failed", exception);
        } catch (IOException exception) {
            throw new SecretException("SEC-PROVIDER-001", "Remote Secret provider request failed", exception);
        } finally {
            if (requestBody != null) java.util.Arrays.fill(requestBody, (byte) 0);
            if (responseBody != null) java.util.Arrays.fill(responseBody, (byte) 0);
        }
    }

    private void authenticate(HttpRequest.Builder builder) {
        if (authentication == null) return;
        switch (authentication.getType()) {
            case NONE -> { }
            case BEARER_TOKEN -> {
                char[] token = authentication.getToken();
                try { if (token.length > 0) builder.header("Authorization", "Bearer " + new String(token)); }
                finally { java.util.Arrays.fill(token, '\0'); }
            }
            case TOKEN_FILE, WORKLOAD_IDENTITY_TOKEN_FILE -> {
                String file = authentication.getType() == RemoteProviderProperties.AuthenticationType.TOKEN_FILE
                        ? authentication.getTokenFile() : authentication.getWorkloadIdentityTokenFile();
                try { builder.header("Authorization", "Bearer " + Files.readString(Path.of(file)).trim()); }
                catch (IOException exception) { throw new SecretException("SEC-AUTH-001", "Provider token file cannot be read", exception); }
            }
            case ACCESS_KEY -> {
                if (authentication.getSignature() != RemoteProviderProperties.RequestSignature.NONE) return;
                if (authentication.getAccessKeyId() != null) builder.header("X-Access-Key-Id", authentication.getAccessKeyId());
                char[] secret = authentication.getAccessKeySecret();
                try { if (secret.length > 0) builder.header("X-Access-Key-Secret", new String(secret)); }
                finally { java.util.Arrays.fill(secret, '\0'); }
            }
        }
    }

    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private String template(String value, Map<String, String> variables) {
        String rendered = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", encode(entry.getValue()));
        }
        return rendered;
    }
    private URI resolve(String suffix) {
        if (!suffix.startsWith("/")) return endpoint.resolve(suffix);
        String path = endpoint.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) return URI.create(endpoint.toString() + suffix);
        String prefix = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        String origin = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        return URI.create(origin + prefix + suffix);
    }
    private String text(JsonNode node, String name, String fallback) {
        JsonNode value = fieldNode(node, name);
        return value != null && !value.isNull() ? value.asText() : fallback;
    }
    private Instant instant(JsonNode node) {
        if (node == null || node.isNull()) return null;
        try { return Instant.parse(node.asText()); } catch (RuntimeException ignored) { return null; }
    }
    private JsonNode fieldNode(JsonNode node, String name) {
        if (node == null || name == null || name.isBlank()) return null;
        JsonNode current = node;
        for (String part : name.split("\\.")) {
            if (current == null || !current.isObject()) return null;
            current = current.get(part);
        }
        return current;
    }
    private Object decodeSecretValue(JsonNode value) {
        if (!"BASE64".equalsIgnoreCase(wire.getSecretValueEncoding()) || !value.isTextual()) {
            return mapper.convertValue(value, Object.class);
        }
        try {
            return new String(Base64.getDecoder().decode(value.asText()), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new SecretException("SEC-STORE-001", "Remote Secret value is not valid Base64", exception);
        }
    }
    private Map<String, String> attributes(JsonNode node) {
        Map<String, String> result = new LinkedHashMap<>();
        if (node != null && node.has("attributes") && node.get("attributes").isObject()) {
            node.get("attributes").fields().forEachRemaining(entry -> result.put(entry.getKey(), entry.getValue().asText()));
        }
        return result;
    }
}
