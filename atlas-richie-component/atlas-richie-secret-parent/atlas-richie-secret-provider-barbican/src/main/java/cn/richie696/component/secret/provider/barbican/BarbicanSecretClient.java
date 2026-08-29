package cn.richie696.component.secret.provider.barbican;

import cn.richie696.component.secret.api.*;
import cn.richie696.component.secret.api.exception.*;
import cn.richie696.component.secret.api.provider.*;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.*;
import cn.richie696.component.secret.core.DestroyableSecretValue;
import cn.richie696.component.secret.provider.common.RemoteHttpClientFactory;
import cn.richie696.component.secret.provider.common.HttpResponseRetryExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Barbican Secret API client. Barbican does not claim KMS wrap/unwrap here. */
public final class BarbicanSecretClient implements SecretBootstrapClient, SecretBackend, SecretProviderSession {
    private static final Set<SecretCapability> CAPABILITIES = Set.of(SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING);
    private final String providerId;
    private final String hash;
    private final BarbicanSecretProperties properties;
    private final BootstrapSecretProperties bootstrap;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final HttpResponseRetryExecutor retryExecutor;
    private final URI endpoint;
    private final char[] token;
    private final AtomicBoolean closed = new AtomicBoolean();
    private BarbicanSecretClient(String providerId, String hash, BarbicanSecretProperties properties, BootstrapSecretProperties bootstrap) {
        this.providerId = providerId; this.hash = hash; this.properties = properties; this.bootstrap = bootstrap;
        this.http = RemoteHttpClientFactory.create(bootstrap.getResilience().getConnectTimeout(), properties.getTls(), properties.getProxy());
        this.retryExecutor = new HttpResponseRetryExecutor(bootstrap.getResilience().getMaxAttempts());
        String root = properties.getEndpoint().toString(); this.endpoint = URI.create(root.endsWith("/") ? root : root + "/");
        this.token = loadToken(properties.getAuthentication());
    }
    static BarbicanSecretClient create(ConfigurableEnvironment environment, BootstrapSecretProperties bootstrap) {
        return create(environment, bootstrap, null);
    }
    static BarbicanSecretClient create(
            ConfigurableEnvironment environment,
            BootstrapSecretProperties bootstrap,
            SecretBootstrapContext context) {
        String prefix = BarbicanSecretProperties.PREFIX, providerId = "barbican";
        if (context != null && context.providerId() != null && !context.providerId().isBlank()) {
            providerId = context.providerId();
            if (context.configurationPrefix() != null && !context.configurationPrefix().isBlank()) prefix = context.configurationPrefix();
        } else {
            String active = bootstrap.getActiveProvider();
            if (active != null && !active.isBlank() && bootstrap.getProviders().containsKey(active)) { prefix = BootstrapSecretProperties.PREFIX + ".providers." + active; providerId = active; }
        }
        BarbicanSecretProperties properties = Binder.get(environment).bind(prefix, BarbicanSecretProperties.class).orElseGet(BarbicanSecretProperties::new);
        BarbicanConfiguration.validate(properties);
        return new BarbicanSecretClient(providerId, BarbicanConfiguration.hash(providerId, properties), properties, bootstrap);
    }
    @Override public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen(); Map<String, Object> values = new LinkedHashMap<>(); List<String> versions = new ArrayList<>();
        for (String logical : request.logicalPaths()) {
            byte[] payload = fetchPayload(id(logical));
            if (payload == null) { if (bootstrap.getPropertySource().getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL) continue; throw new SecretBootstrapException("SEC-STORE-001", "Barbican Secret is missing"); }
            try { values.put(logical, decodePropertyValue(payload)); }
            finally { Arrays.fill(payload, (byte) 0); }
            versions.add(logical);
        }
        return new SecretBootstrapResult(providerId, digest(versions), String.join(",", request.logicalPaths()), Instant.now(), values, null);
    }
    @Override public SecretValue read(SecretReference reference) {
        ensureOpen(); byte[] payload = fetchPayload(id(reference.logicalName()));
        if (payload == null) throw new SecretException("SEC-STORE-001", "Barbican Secret is missing: " + reference.logicalName());
        try { return DestroyableSecretValue.ofBytes(payload); } finally { Arrays.fill(payload, (byte) 0); }
    }
    @Override public SecretMetadata metadata(SecretReference reference) {
        ensureOpen(); JsonNode node = send("GET", "v1/secrets/" + encode(id(reference.logicalName())), null, true);
        if (node == null) throw new SecretException("SEC-STORE-001", "Barbican Secret metadata is missing: " + reference.logicalName());
        return new SecretMetadata(node.path("updated").asText(node.path("created").asText("latest")), parse(node.path("created").asText(null)), parse(node.path("expiration").asText(null)), Map.of("provider", "barbican", "status", node.path("status").asText("unknown")));
    }
    @Override public SecretProviderDescriptor descriptor() { return new SecretProviderDescriptor("barbican", providerId, CAPABILITIES); }
    @Override public Optional<SecretBackend> secretBackend() { return Optional.of(this); }
    @Override public Optional<cn.richie696.component.secret.api.crypto.KeyWrappingBackend> keyWrappingBackend() { return Optional.empty(); }
    String configurationHash() { return hash; }
    @Override public void close() { if (closed.compareAndSet(false, true)) Arrays.fill(token, '\0'); }
    private byte[] fetchPayload(String id) {
        JsonNode metadata = send("GET", "v1/secrets/" + encode(id), null, true);
        if (metadata == null) return null;
        byte[] responseBody = null;
        boolean ownershipTransferred = false;
        try {
            HttpRequest request = request("GET", "v1/secrets/" + encode(id) + "/payload", null).header("Accept", "application/octet-stream").build();
            HttpResponse<byte[]> response = retryExecutor.send(http, request);
            responseBody = response.body();
            if (response.statusCode() == 404) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerFailure("Barbican payload request", response.statusCode());
            ownershipTransferred = true;
            return responseBody;
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new SecretException("SEC-PROVIDER-001", "Barbican request interrupted", exception); }
        catch (IOException exception) { throw new SecretException("SEC-PROVIDER-001", "Barbican payload request failed", exception); }
        finally {
            if (!ownershipTransferred && responseBody != null) Arrays.fill(responseBody, (byte) 0);
        }
    }
    private JsonNode send(String method, String suffix, Object body, boolean missingIsNull) {
        byte[] requestBody = null;
        byte[] responseBody = null;
        try {
            HttpRequest.Builder builder = baseRequest(suffix);
            if ("GET".equals(method)) {
                builder.GET();
            } else {
                requestBody = mapper.writeValueAsBytes(body);
                builder.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
            }
            HttpResponse<byte[]> response = retryExecutor.send(http, builder.build());
            responseBody = response.body();
            if (response.statusCode() == 404 && missingIsNull) return null;
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw providerFailure("Barbican request", response.statusCode());
            return responseBody.length == 0 ? mapper.createObjectNode() : mapper.readTree(responseBody);
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new SecretException("SEC-PROVIDER-001", "Barbican request interrupted", exception); }
        catch (IOException | RuntimeException exception) { if (exception instanceof SecretException secret) throw secret; throw new SecretException("SEC-PROVIDER-001", "Barbican request failed", exception); }
        finally {
            if (requestBody != null) Arrays.fill(requestBody, (byte) 0);
            if (responseBody != null) Arrays.fill(responseBody, (byte) 0);
        }
    }
    private HttpRequest.Builder request(String method, String suffix, Object body) {
        HttpRequest.Builder builder = baseRequest(suffix);
        return "GET".equals(method) ? builder.GET() : builder.POST(HttpRequest.BodyPublishers.noBody());
    }
    private HttpRequest.Builder baseRequest(String suffix) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint.resolve(suffix))
                .timeout(bootstrap.getResilience().getReadTimeout())
                .header("X-Auth-Token", new String(token));
        if (properties.getProjectId() != null && !properties.getProjectId().isBlank()) {
            builder.header("X-Project-Id", properties.getProjectId());
        }
        return builder;
    }
    private String id(String logical) { BarbicanSecretProperties.SecretMapping mapping = properties.getSecrets().get(logical); return mapping == null ? logical : mapping.getId(); }
    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("Barbican Secret Provider is closed"); }
    private static String encode(String value) { return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static char[] loadToken(BarbicanSecretProperties.Authentication authentication) { try { if (authentication.getType() == BarbicanSecretProperties.AuthenticationType.TOKEN_FILE) return Files.readString(Path.of(authentication.getTokenFile()), StandardCharsets.UTF_8).trim().toCharArray(); char[] token = authentication.getToken(); if (token.length == 0) throw new IllegalArgumentException("Barbican token must not be empty"); return token; } catch (IOException exception) { throw new IllegalArgumentException("Barbican token file cannot be read", exception); } }
    private static SecretException providerFailure(String operation, int status) {
        String code = status == 401 ? "SEC-AUTH-001" : status == 403 ? "SEC-AUTHZ-001" : status == 404 ? "SEC-STORE-001" : "SEC-PROVIDER-001";
        return new SecretException(code, operation + " failed with HTTP " + status);
    }
    private static Instant parse(String value) { try { return value == null ? null : Instant.parse(value); } catch (RuntimeException ignored) { return null; } }
    static String decodePropertyValue(byte[] payload) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(payload))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new SecretConfigurationException(
                    "SEC-STORE-003", "Barbican property-source Secret payload must be valid UTF-8", exception);
        }
    }
    private static String digest(List<String> values) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
