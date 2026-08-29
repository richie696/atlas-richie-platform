package cn.richie696.component.secret.provider.common;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import cn.richie696.component.secret.core.DestroyableSecretValue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared strict lifecycle implementation for optional REST-based providers. */
public final class RemoteSecretProviderClient implements SecretBootstrapClient, SecretBackend,
        KeyWrappingBackend, SecretProviderSession {
    private final String providerType;
    private final String providerId;
    private final String configurationHash;
    private final RemoteProviderProperties properties;
    private final BootstrapSecretProperties bootstrap;
    private final Set<SecretCapability> capabilities;
    private final RemoteSecretTransport transport;
    private final AtomicBoolean closed = new AtomicBoolean();

    public RemoteSecretProviderClient(String providerType, String providerId, String configurationHash,
                                      RemoteProviderProperties properties, BootstrapSecretProperties bootstrap,
                                      Set<SecretCapability> capabilities, RemoteSecretTransport transport) {
        this.providerType = providerType;
        this.providerId = providerId;
        this.configurationHash = configurationHash;
        this.properties = properties;
        this.bootstrap = bootstrap;
        this.capabilities = Set.copyOf(capabilities);
        this.transport = transport;
    }

    @Override
    public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        if (!capabilities.contains(SecretCapability.SECRET_READ)) {
            // KMS-only providers still participate in bootstrap so the runtime
            // key-wrapping backend can be exposed without pretending to be a
            // Secret Store or injecting an empty fake property source.
            return new SecretBootstrapResult(providerId, "kms-only", "kms-only",
                    Instant.now(), Map.of(), null);
        }
        require(SecretCapability.SECRET_READ);
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> versions = new ArrayList<>();
        for (String path : request.logicalPaths()) {
            RemoteSecretTransport.RemoteValue value = transport.read(path, SecretVersionSelector.latest());
            if (value == null) {
                if (bootstrap.getPropertySource().getMissingPolicy() == BootstrapSecretProperties.MissingPolicy.LOCAL) continue;
                throw new SecretBootstrapException("SEC-STORE-001", providerType + " bootstrap Secret is missing");
            }
            if (value.value() instanceof Map<?, ?> map) {
                flattenInto(merged, map, "");
            } else {
                merged.put(path, value.value());
            }
            versions.add(path + "@" + value.version());
        }
        return new SecretBootstrapResult(providerId, digest(versions), String.join(",", request.logicalPaths()),
                Instant.now(), merged, null);
    }

    @Override
    public SecretValue read(SecretReference reference) {
        ensureOpen();
        require(SecretCapability.SECRET_READ);
        RemoteProviderProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        String path = mapping == null ? defaultPath(reference.logicalName()) : mapping.getPath();
        RemoteSecretTransport.RemoteValue remote = transport.read(path, reference.version());
        if (remote == null) throw new SecretException("SEC-STORE-001", "Secret is missing: " + reference.logicalName());
        Object value = remote.value();
        String field = reference.field() == null && mapping != null ? mapping.getField() : reference.field();
        if (field != null) {
            if (!(value instanceof Map<?, ?> map) || !map.containsKey(field)) {
                throw new SecretException("SEC-STORE-001", "Secret field is missing: " + reference.logicalName());
            }
            value = map.get(field);
        }
        byte[] bytes = scalarBytes(value, reference.logicalName());
        try { return DestroyableSecretValue.ofBytes(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    @Override
    public SecretMetadata metadata(SecretReference reference) {
        ensureOpen();
        require(SecretCapability.SECRET_READ);
        RemoteProviderProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        RemoteSecretTransport.RemoteValue remote = transport.read(
                mapping == null ? defaultPath(reference.logicalName()) : mapping.getPath(), reference.version());
        if (remote == null) throw new SecretException("SEC-STORE-001", "Secret metadata is missing: " + reference.logicalName());
        return new SecretMetadata(remote.version(), remote.createdAt(), null,
                mergeAttributes(remote.attributes(), Map.of("provider", providerType)));
    }

    @Override
    public WrappedKey wrap(KeyReference key, byte[] plaintext, CryptoContext context) {
        ensureOpen();
        require(SecretCapability.KEY_WRAP);
        String physical = properties.getKeyBindings().get(key.logicalKey());
        if (physical == null || physical.isBlank()) {
            throw new SecretConfigurationException("SEC-KEY-001", "No " + providerType + " key binding exists for " + key.logicalKey());
        }
        return new WrappedKey(
                transport.wrap(physical, plaintext, providerContext(key, context)),
                providerType + "-remote");
    }

    @Override
    public byte[] unwrap(KeyReference key, WrappedKey wrapped, CryptoContext context) {
        ensureOpen();
        require(SecretCapability.KEY_UNWRAP);
        if (!wrapped.algorithm().equals(providerType + "-remote")) {
            throw new SecretConfigurationException("SEC-CRYPTO-002", "Wrapped key algorithm is not supported by " + providerType);
        }
        String physical = properties.getKeyBindings().get(key.logicalKey());
        if (physical == null || physical.isBlank()) {
            throw new SecretConfigurationException("SEC-KEY-001", "No " + providerType + " key binding exists for " + key.logicalKey());
        }
        byte[] value = wrapped.value();
        try { return transport.unwrap(physical, value, providerContext(key, context)); }
        finally { Arrays.fill(value, (byte) 0); }
    }

    @Override public SecretProviderDescriptor descriptor() {
        return new SecretProviderDescriptor(providerType, providerId, capabilities);
    }
    @Override public java.util.Optional<SecretBackend> secretBackend() {
        return capabilities.contains(SecretCapability.SECRET_READ) ? java.util.Optional.of(this) : java.util.Optional.empty();
    }
    @Override public java.util.Optional<KeyWrappingBackend> keyWrappingBackend() {
        return capabilities.contains(SecretCapability.KEY_WRAP) ? java.util.Optional.of(this) : java.util.Optional.empty();
    }
    public String configurationHash() { return configurationHash; }
    @Override public void close() { if (closed.compareAndSet(false, true)) transport.close(); }

    private String defaultPath(String logicalName) {
        var source = bootstrap.getPropertySource();
        return "atlas-richie/" + source.getEnvironment() + "/" + source.getApplication() + "/runtime/" + logicalName;
    }
    private CryptoContext providerContext(KeyReference key, CryptoContext context) {
        CryptoContext source = context == null ? CryptoContext.empty() : context;
        Map<String, String> attributes = new LinkedHashMap<>(source.attributes());
        attributes.put("atlas.secret.key", key.logicalKey());
        attributes.put("atlas.secret.version", key.version());
        attributes.put("atlas.secret.purpose", key.purpose().name());
        return new CryptoContext(source.associatedData(), attributes);
    }
    private byte[] scalarBytes(Object value, String logicalName) {
        if (value instanceof byte[] bytes) return bytes.clone();
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value.toString().getBytes(StandardCharsets.UTF_8);
        }
        throw new SecretConfigurationException(
                "SEC-STORE-003", "Remote Secret field is not scalar: " + logicalName);
    }
    private void require(SecretCapability capability) {
        if (!capabilities.contains(capability)) throw new SecretConfigurationException("SEC-CAP-001", providerType + " does not support " + capability);
    }
    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("Secret provider session is closed"); }
    private void flattenInto(Map<String, Object> target, Map<?, ?> source, String prefix) {
        source.forEach((key, value) -> {
            String name = prefix.isEmpty() ? String.valueOf(key) : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) flattenInto(target, nested, name); else target.put(name, value);
        });
    }
    private String digest(List<String> values) {
        try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(String.join("\n", values).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private Map<String, String> mergeAttributes(Map<String, String> first, Map<String, String> second) {
        Map<String, String> result = new LinkedHashMap<>(first); result.putAll(second); return result;
    }
}
