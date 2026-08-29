package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Strict JSON transport used by providers whose vendor SDK is optional. The
 * endpoint is a vendor REST endpoint or a local contract-test facade; no
 * plaintext fallback is ever performed.
 */
public final class JsonHttpSecretTransport implements RemoteSecretTransport {
    private final URI secretEndpoint;
    private final URI kmsEndpoint;
    private final RemoteProviderProperties.Authentication authentication;
    private final RemoteProviderProperties.Wire wire;
    private final Map<String, String> variables;
    private final Duration timeout;
    private final HttpClient client;
    private final HttpResponseRetryExecutor retryExecutor;
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
        this(endpoint, endpoint, authentication, wire, variables, new RemoteProviderProperties.Tls(),
                new RemoteProviderProperties.Proxy(), connectTimeout, readTimeout, 1);
    }

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Map<String, String> variables,
                                   RemoteProviderProperties.Tls tls,
                                   RemoteProviderProperties.Proxy proxy,
                                   Duration connectTimeout,
                                   Duration readTimeout) {
        this(endpoint, endpoint, authentication, wire, variables, tls, proxy, connectTimeout, readTimeout, 1);
    }

    public JsonHttpSecretTransport(URI endpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Map<String, String> variables,
                                   RemoteProviderProperties.Tls tls,
                                   RemoteProviderProperties.Proxy proxy,
                                   Duration connectTimeout,
                                   Duration readTimeout,
                                   int maxAttempts) {
        this(endpoint, endpoint, authentication, wire, variables, tls, proxy,
                connectTimeout, readTimeout, maxAttempts);
    }

    public JsonHttpSecretTransport(URI secretEndpoint,
                                   URI kmsEndpoint,
                                   RemoteProviderProperties.Authentication authentication,
                                   RemoteProviderProperties.Wire wire,
                                   Map<String, String> variables,
                                   RemoteProviderProperties.Tls tls,
                                   RemoteProviderProperties.Proxy proxy,
                                   Duration connectTimeout,
                                   Duration readTimeout,
                                   int maxAttempts) {
        this.secretEndpoint = normalizeEndpoint(secretEndpoint, "Secret");
        this.kmsEndpoint = normalizeEndpoint(kmsEndpoint, "KMS");
        this.authentication = authentication;
        this.wire = wire == null ? new RemoteProviderProperties.Wire() : wire;
        this.variables = variables == null ? Map.of() : Map.copyOf(variables);
        this.timeout = readTimeout == null ? Duration.ofSeconds(5) : readTimeout;
        this.client = RemoteHttpClientFactory.create(connectTimeout, tls, proxy);
        this.retryExecutor = new HttpResponseRetryExecutor(maxAttempts);
    }

    @Override
    public RemoteValue read(String path, SecretVersionSelector selector) {
        boolean latest = selector == null || selector.type() == SecretVersionSelector.Type.LATEST;
        String version = latest ? wire.getLatestVersionValue() : selector.value();
        Map<String, String> pathVariables = new LinkedHashMap<>(variables);
        pathVariables.put("path", path);
        pathVariables.put("version", version);
        String pathTemplate = latest && !wire.getSecretLatestPath().isBlank()
                ? wire.getSecretLatestPath() : wire.getSecretPath();
        String suffix = template(pathTemplate, pathVariables);
        if (!latest
                && wire.getSecretMethod() == RemoteProviderProperties.HttpMethod.GET
                && wire.getSecretRequestVersionField().isBlank()
                && !pathTemplate.contains("{version}")) {
            suffix += (suffix.contains("?") ? "&" : "?") + "version=" + encode(selector.value());
        }
        Object body = wire.getSecretMethod() == RemoteProviderProperties.HttpMethod.POST
                ? secretReadBody(path, version) : null;
        JsonNode node = send(wire.getSecretMethod().name(), suffix, body, Operation.SECRET_READ);
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
            addKeyVariables(pathVariables, key, wire.getWrapPath());
            JsonNode node = send("POST", template(wire.getWrapPath(), pathVariables),
                    cryptoBody(key, plaintext, context, true), Operation.WRAP);
            String value = text(node, wire.getWrappedKeyField(), null);
            if (value == null) throw new SecretCryptoException("SEC-CRYPTO-001", "Remote KMS returned no wrapped key");
            return decodeCryptoValue(value);
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
            addKeyVariables(pathVariables, key, wire.getUnwrapPath());
            JsonNode node = send("POST", template(wire.getUnwrapPath(), pathVariables),
                    cryptoBody(key, wrapped, context, false), Operation.UNWRAP);
            String value = text(node, wire.getPlaintextField(), null);
            if (value == null) throw new SecretCryptoException("SEC-CRYPTO-002", "Remote KMS returned no plaintext");
            return decodeCryptoValue(value);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Remote KMS unwrap failed", exception);
        }
    }

    private Map<String, Object> cryptoBody(String key, byte[] value, CryptoContext context, boolean wrapping) {
        byte[] aad = canonicalAssociatedData(context);
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            String valueField = wrapping ? wire.getRequestWrapValueField() : wire.getRequestUnwrapValueField();
            if (valueField == null || valueField.isBlank()) valueField = wire.getRequestValueField();
            body.put(valueField, encodeCryptoValue(value));
            if (!wire.getRequestAlgorithmField().isBlank() && !wire.getRequestAlgorithm().isBlank()) {
                body.put(wire.getRequestAlgorithmField(), wire.getRequestAlgorithm());
            }
            boolean hasAad = aad.length > 0 || (context != null && !context.attributes().isEmpty());
            if (hasAad && !wire.getRequestAadField().isBlank()
                    && wire.getRequestAadEncoding() == RemoteProviderProperties.RequestAadEncoding.ATTRIBUTES) {
                Map<String, String> attributes = new TreeMap<>();
                if (context != null) attributes.putAll(context.attributes());
                if (aad.length > 0) attributes.put("atlas.secret.associated-data-sha256", sha256(aad));
                body.put(wire.getRequestAadField(), attributes);
            } else if (hasAad && !wire.getRequestAadField().isBlank()) {
                body.put(wire.getRequestAadField(), Base64.getEncoder().encodeToString(aad));
            }
            if (wire.getRequestKeyField() != null && !wire.getRequestKeyField().isBlank()) {
                body.put(wire.getRequestKeyField(), key);
            }
            return body;
        } finally {
            java.util.Arrays.fill(aad, (byte) 0);
        }
    }

    private byte[] canonicalAssociatedData(CryptoContext context) {
        if (context == null) return new byte[0];
        byte[] source = context.associatedData();
        if (context.attributes().isEmpty()) return source;
        byte[] attributes = new TreeMap<>(context.attributes()).entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("\n"))
                .getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[source.length + 1 + attributes.length];
        try {
            System.arraycopy(source, 0, result, 0, source.length);
            System.arraycopy(attributes, 0, result, source.length + 1, attributes.length);
            return result;
        } finally {
            java.util.Arrays.fill(source, (byte) 0);
            java.util.Arrays.fill(attributes, (byte) 0);
        }
    }

    private String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private JsonNode send(String method, String suffix, Object body, Operation operation) {
        byte[] requestBody = null;
        byte[] responseBody = null;
        try {
            URI requestUri = resolve(operation == Operation.SECRET_READ ? secretEndpoint : kmsEndpoint, suffix);
            HttpRequest.Builder builder = HttpRequest.newBuilder(requestUri)
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
            RemoteRequestSigner.apply(builder, requestUri, method,
                    requestBody == null ? new byte[0] : requestBody, authentication, signingVariables(operation));
            HttpResponse<byte[]> response = retryExecutor.send(client, builder.build());
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

    private String encode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length);
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if ((unsigned >= 'a' && unsigned <= 'z')
                    || (unsigned >= 'A' && unsigned <= 'Z')
                    || (unsigned >= '0' && unsigned <= '9')
                    || unsigned == '-' || unsigned == '.' || unsigned == '_' || unsigned == '~') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                encoded.append(Character.toUpperCase(Character.forDigit((unsigned >>> 4) & 0xf, 16)));
                encoded.append(Character.toUpperCase(Character.forDigit(unsigned & 0xf, 16)));
            }
        }
        return encoded.toString();
    }
    private String template(String value, Map<String, String> variables) {
        String rendered = value;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{" + entry.getKey() + "}", encode(entry.getValue()));
        }
        return rendered;
    }
    private URI resolve(URI endpoint, String suffix) {
        if (!suffix.startsWith("/")) return endpoint.resolve(suffix);
        String path = endpoint.getPath();
        if (path == null || path.isBlank() || "/".equals(path)) {
            return "/".equals(suffix) ? endpoint.resolve("/") : URI.create(endpoint.toString() + suffix);
        }
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
        if (node.isNumber()) {
            long timestamp = node.asLong();
            return timestamp > 10_000_000_000L ? Instant.ofEpochMilli(timestamp) : Instant.ofEpochSecond(timestamp);
        }
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

    private Map<String, Object> secretReadBody(String path, String version) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!wire.getSecretRequestNameField().isBlank()) body.put(wire.getSecretRequestNameField(), path);
        if (!wire.getSecretRequestVersionField().isBlank()) body.put(wire.getSecretRequestVersionField(), version);
        return body;
    }

    private void addKeyVariables(Map<String, String> target, String physicalKey, String template) {
        if (!template.contains("{version}")) {
            target.put("key", physicalKey);
            return;
        }
        int separator = physicalKey.lastIndexOf('/');
        if (separator <= 0 || separator == physicalKey.length() - 1) {
            throw new SecretConfigurationException(
                    "SEC-KEY-001",
                    "Versioned KMS binding must use the form <key-name>/<key-version>");
        }
        target.put("key", physicalKey.substring(0, separator));
        target.put("version", physicalKey.substring(separator + 1));
    }

    private String encodeCryptoValue(byte[] value) {
        return wire.getRequestValueEncoding() == RemoteProviderProperties.ValueEncoding.BASE64_URL
                ? Base64.getUrlEncoder().withoutPadding().encodeToString(value)
                : Base64.getEncoder().encodeToString(value);
    }

    private byte[] decodeCryptoValue(String value) {
        return wire.getResponseValueEncoding() == RemoteProviderProperties.ValueEncoding.BASE64_URL
                ? Base64.getUrlDecoder().decode(value)
                : Base64.getDecoder().decode(value);
    }

    private Map<String, String> signingVariables(Operation operation) {
        Map<String, String> result = new LinkedHashMap<>(variables);
        switch (operation) {
            case SECRET_READ -> {
                result.put("apiAction", wire.getSecretAction());
                result.put("apiVersion", wire.getSecretApiVersion());
                result.put("signingService", wire.getSecretSigningService());
            }
            case WRAP -> {
                result.put("apiAction", wire.getWrapAction());
                result.put("apiVersion", wire.getKmsApiVersion());
                result.put("signingService", wire.getKmsSigningService());
            }
            case UNWRAP -> {
                result.put("apiAction", wire.getUnwrapAction());
                result.put("apiVersion", wire.getKmsApiVersion());
                result.put("signingService", wire.getKmsSigningService());
            }
        }
        return result;
    }

    private URI normalizeEndpoint(URI value, String label) {
        if (value == null || value.getHost() == null) {
            throw new IllegalArgumentException(label + " provider endpoint must include a host");
        }
        return value.toString().endsWith("/")
                ? URI.create(value.toString().substring(0, value.toString().length() - 1)) : value;
    }

    private enum Operation { SECRET_READ, WRAP, UNWRAP }
}
