package cn.richie696.component.secret.provider.openbao;

import cn.richie696.component.secret.api.*;
import cn.richie696.component.secret.api.crypto.*;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.*;
import cn.richie696.component.secret.core.DestroyableSecretValue;
import cn.richie696.component.secret.provider.common.RemoteHttpClientFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 独立的 OpenBao HTTP Provider；不复用 Vault SDK 或 Vault Provider 标识。 */
public final class OpenBaoSecretClient implements SecretBootstrapClient, SecretBackend,
        KeyWrappingBackend, SigningBackend, SecretProviderSession {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP,
            SecretCapability.SIGN, SecretCapability.VERIFY);
    private final String providerId;
    private final String configurationHash;
    private final OpenBaoSecretProperties properties;
    private final BootstrapSecretProperties bootstrap;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final URI endpoint;
    private final char[] token;
    private final AtomicBoolean closed = new AtomicBoolean();

    OpenBaoSecretClient(String providerId, String configurationHash,
                        OpenBaoSecretProperties properties, BootstrapSecretProperties bootstrap) {
        this.providerId = providerId;
        this.configurationHash = configurationHash;
        this.properties = properties;
        this.bootstrap = bootstrap;
        this.mapper = new ObjectMapper();
        this.http = RemoteHttpClientFactory.create(bootstrap.getResilience().getConnectTimeout(), properties.getTls(), properties.getProxy());
        String root = properties.getEndpoint().toString();
        this.endpoint = URI.create(root.endsWith("/") ? root : root + "/");
        this.token = loadToken(properties.getAuthentication());
    }

    static OpenBaoSecretClient create(ConfigurableEnvironment environment,
                                      BootstrapSecretProperties bootstrap) {
        String prefix = OpenBaoSecretProperties.PREFIX;
        String active = bootstrap.getActiveProvider();
        String providerId = "openbao";
        if (active != null && !active.isBlank() && bootstrap.getProviders().containsKey(active)) {
            prefix = BootstrapSecretProperties.PREFIX + ".providers." + active;
            providerId = active;
        }
        OpenBaoSecretProperties properties = Binder.get(environment)
                .bind(prefix, OpenBaoSecretProperties.class)
                .orElseGet(OpenBaoSecretProperties::new);
        OpenBaoSecretConfiguration.validate(properties);
        return new OpenBaoSecretClient(providerId,
                OpenBaoSecretConfiguration.hash(providerId, properties), properties, bootstrap);
    }

    @Override public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> versions = new ArrayList<>();
        for (String logicalPath : request.logicalPaths()) {
            JsonNode data = read(logicalPath, null);
            if (data == null || !data.isObject()) {
                if (bootstrap.getPropertySource().getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL) continue;
                throw new SecretBootstrapException("SEC-STORE-001", "OpenBao bootstrap Secret Bundle is missing");
            }
            JsonNode payload = data.path("data").path("data");
            if (!payload.isObject()) payload = data.path("data");
            if (!payload.isObject()) payload = data;
            merge(values, mapper.convertValue(payload, new TypeReference<Map<String, Object>>() { }));
            String version = data.path("metadata").path("version").asText("latest");
            versions.add(logicalPath + "@" + version);
        }
        return new SecretBootstrapResult(providerId, digest(versions), String.join(",", request.logicalPaths()),
                Instant.now(), values, null);
    }

    @Override public SecretValue read(SecretReference reference) {
        ensureOpen();
        OpenBaoSecretProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        String path = mapping == null ? defaultPath(reference.logicalName()) : mapping.getPath();
        JsonNode envelope = read(path, reference.version().type() == SecretVersionSelector.Type.VERSION
                ? reference.version().value() : null);
        if (envelope == null) throw new SecretException("SEC-STORE-001", "Secret is missing: " + reference.logicalName());
        JsonNode data = envelope.path("data").path("data");
        if (!data.isObject()) data = envelope.path("data");
        String field = reference.field() != null ? reference.field() : mapping == null ? null : mapping.getField();
        JsonNode selected = field == null ? data : data.get(field);
        if (selected == null || selected.isMissingNode() || selected.isNull())
            throw new SecretException("SEC-STORE-001", "Secret field is missing: " + reference.logicalName());
        byte[] bytes = selected.isTextual() ? selected.textValue().getBytes(StandardCharsets.UTF_8)
                : selected.toString().getBytes(StandardCharsets.UTF_8);
        try { return DestroyableSecretValue.ofBytes(bytes); } finally { Arrays.fill(bytes, (byte) 0); }
    }

    @Override public SecretMetadata metadata(SecretReference reference) {
        ensureOpen();
        OpenBaoSecretProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        JsonNode envelope = read(mapping == null ? defaultPath(reference.logicalName()) : mapping.getPath(), null);
        if (envelope == null) throw new SecretException("SEC-STORE-001", "Secret metadata is missing: " + reference.logicalName());
        JsonNode metadata = envelope.path("data").path("metadata");
        return new SecretMetadata(metadata.path("version").asText("latest"),
                parseInstant(metadata.path("created_time").asText(null)), null,
                Map.of("provider", "openbao", "mount", properties.getKv().getMount()));
    }

    @Override public WrappedKey wrap(KeyReference reference, byte[] plaintextKey, CryptoContext context) {
        ensureOpen();
        if (plaintextKey == null || plaintextKey.length == 0) throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        String key = physicalKey(reference);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("plaintext", Base64.getEncoder().encodeToString(plaintextKey));
        addKeyVersion(body, reference.version());
        JsonNode response = post("v1/" + segment(properties.getTransit().getMount()) + "/encrypt/" + segment(key), body);
        String ciphertext = response.path("data").path("ciphertext").asText(null);
        if (ciphertext == null || ciphertext.isBlank()) throw new SecretCryptoException("SEC-CRYPTO-001", "OpenBao did not return ciphertext");
        return new WrappedKey(ciphertext.getBytes(StandardCharsets.UTF_8), "openbao-transit");
    }

    @Override public byte[] unwrap(KeyReference reference, WrappedKey wrappedKey, CryptoContext context) {
        ensureOpen();
        if (!"openbao-transit".equals(wrappedKey.algorithm())) throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped key algorithm is not supported by OpenBao");
        byte[] encoded = wrappedKey.value();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ciphertext", new String(encoded, StandardCharsets.UTF_8));
            addKeyVersion(body, reference.version());
            String plaintext = post("v1/" + segment(properties.getTransit().getMount()) + "/decrypt/" + segment(physicalKey(reference)), body)
                    .path("data").path("plaintext").asText(null);
            if (plaintext == null) throw new SecretCryptoException("SEC-CRYPTO-002", "OpenBao did not return plaintext");
            return Base64.getDecoder().decode(plaintext);
        } finally { Arrays.fill(encoded, (byte) 0); }
    }

    @Override public SignatureValue sign(KeyReference reference, byte[] payload, CryptoContext context) {
        ensureOpen();
        validateSigningKey(reference);
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-001", "Signing payload must not be empty");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(payload));
        addKeyVersion(body, reference.version());
        JsonNode response = post("v1/" + segment(properties.getTransit().getMount())
                + "/sign/" + segment(physicalKey(reference)), body);
        String signature = response.path("data").path("signature").asText(null);
        if (signature == null || signature.isBlank()) {
            throw new SecretCryptoException("SEC-SIGN-001", "OpenBao did not return a signature");
        }
        return new SignatureValue(signature);
    }

    @Override public boolean verify(
            KeyReference reference, byte[] payload, SignatureValue signature, CryptoContext context) {
        ensureOpen();
        validateSigningKey(reference);
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-002", "Verification payload must not be empty");
        }
        if (signature == null) {
            throw new SecretCryptoException("SEC-SIGN-002", "Signature must not be null");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", Base64.getEncoder().encodeToString(payload));
        body.put("signature", signature.value());
        addKeyVersion(body, reference.version());
        JsonNode response = post("v1/" + segment(properties.getTransit().getMount())
                + "/verify/" + segment(physicalKey(reference)), body);
        return response.path("data").path("valid").asBoolean(false);
    }

    @Override public SecretProviderDescriptor descriptor() { return new SecretProviderDescriptor("openbao", providerId, CAPABILITIES); }
    @Override public Optional<SecretBackend> secretBackend() { return Optional.of(this); }
    @Override public Optional<KeyWrappingBackend> keyWrappingBackend() { return Optional.of(this); }
    @Override public Optional<SigningBackend> signingBackend() { return Optional.of(this); }
    String configurationHash() { return configurationHash; }
    @Override public void close() { if (closed.compareAndSet(false, true)) Arrays.fill(token, '\0'); }

    private JsonNode read(String path, String version) {
        String suffix = "v1/" + segment(properties.getKv().getMount()) + "/data/" + path(path);
        if (version != null) suffix += "?version=" + segment(version);
        return send("GET", suffix, null, true);
    }
    private JsonNode post(String suffix, Object body) { return send("POST", suffix, body, false); }
    private JsonNode send(String method, String suffix, Object body, boolean missingIsNull) {
        byte[] requestBody = null;
        byte[] responseBody = null;
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve(suffix))
                    .timeout(bootstrap.getResilience().getReadTimeout()).header("X-Vault-Token", new String(token));
            if (properties.getNamespace() != null && !properties.getNamespace().isBlank()) builder.header("X-Vault-Namespace", properties.getNamespace());
            if ("GET".equals(method)) builder.GET();
            else {
                requestBody = mapper.writeValueAsBytes(body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            }
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            responseBody = response.body();
            if (response.statusCode() == 404 && missingIsNull) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerFailure("OpenBao request", response.statusCode());
            return mapper.readTree(responseBody);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new SecretException("SEC-PROVIDER-001", "OpenBao request interrupted", exception); }
        catch (IOException | RuntimeException exception) {
            if (exception instanceof SecretException secret) throw secret;
            throw new SecretException("SEC-PROVIDER-001", "OpenBao request failed", exception);
        } finally {
            if (requestBody != null) Arrays.fill(requestBody, (byte) 0);
            if (responseBody != null) Arrays.fill(responseBody, (byte) 0);
        }
    }
    private String physicalKey(KeyReference reference) { return properties.getTransit().getKeyBindings().getOrDefault(reference.logicalKey(), reference.logicalKey()); }
    private void validateSigningKey(KeyReference reference) {
        if (reference == null || reference.purpose() != KeyPurpose.SIGNING) {
            throw new SecretConfigurationException("SEC-KEY-003", "Signing requires a KeyReference with SIGNING purpose");
        }
    }
    private String defaultPath(String logical) { return properties.getKv().getRuntimePrefix() + "/" + logical; }
    private void addKeyVersion(Map<String, Object> body, String version) { try { if (version != null && !"current".equalsIgnoreCase(version)) body.put("key_version", Integer.parseInt(version)); } catch (NumberFormatException ignored) { } }
    private void merge(Map<String, Object> target, Map<String, Object> source) { source.forEach((key, value) -> { if (target.containsKey(key) && !Objects.equals(target.get(key), value)) throw new SecretBootstrapException("SEC-STORE-002", "Ambiguous OpenBao Secret binding: " + key); target.put(key, value); }); }
    private String digest(List<String> values) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private Instant parseInstant(String value) { try { return value == null ? null : Instant.parse(value); } catch (RuntimeException ignored) { return null; } }
    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("OpenBao Secret Provider is closed"); }
    private static String segment(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String path(String value) { return Arrays.stream(value.split("/", -1)).map(OpenBaoSecretClient::segment).reduce((a, b) -> a + "/" + b).orElseThrow(); }
    private static char[] loadToken(OpenBaoSecretProperties.Authentication authentication) {
        try {
            if (authentication.getType() == OpenBaoSecretProperties.AuthenticationType.TOKEN_FILE) return Files.readString(Path.of(authentication.getTokenFile()), StandardCharsets.UTF_8).trim().toCharArray();
            char[] token = authentication.getToken();
            if (token.length == 0) throw new IllegalArgumentException("OpenBao token must not be empty");
            return token;
        } catch (IOException exception) { throw new IllegalArgumentException("OpenBao token file cannot be read", exception); }
    }
    private static SecretException providerFailure(String operation, int status) {
        String code = status == 401 ? "SEC-AUTH-001" : status == 403 ? "SEC-AUTHZ-001" : status == 404 ? "SEC-STORE-001" : "SEC-PROVIDER-001";
        return new SecretException(code, operation + " failed with HTTP " + status);
    }
}
