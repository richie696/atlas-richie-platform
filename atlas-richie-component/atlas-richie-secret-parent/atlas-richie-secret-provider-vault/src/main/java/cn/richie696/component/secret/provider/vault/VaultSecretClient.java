/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.vault;

import cn.richie696.component.secret.api.SecretBackend;
import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.SecretMetadata;
import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretValue;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyWrappingBackend;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.SigningBackend;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import cn.richie696.component.secret.core.DestroyableSecretValue;
import org.springframework.vault.core.VaultOperations;
import org.springframework.vault.core.VaultTransitOperations;
import org.springframework.vault.core.VaultVersionedKeyValueOperations;
import org.springframework.vault.support.VaultTransitContext;
import org.springframework.vault.support.Plaintext;
import org.springframework.vault.support.Signature;
import org.springframework.vault.support.Versioned;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vault KV v2 与 Transit 的线程安全 Provider 会话。
 */
public final class VaultSecretClient implements
        SecretBootstrapClient,
        SecretBackend,
        KeyWrappingBackend,
        SigningBackend,
        SecretProviderSession {

    private static final Logger log = LoggerFactory.getLogger(VaultSecretClient.class);
    private static final String WRAPPING_ALGORITHM = "vault-transit";
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ,
            SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP,
            SecretCapability.KEY_UNWRAP,
            SecretCapability.SIGN,
            SecretCapability.VERIFY);

    private final String providerId;
    private final String configurationHash;
    private final VaultSecretProperties properties;
    private final BootstrapSecretProperties bootstrapProperties;
    private final VaultVersionedKeyValueOperations kvOperations;
    private final VaultTransitOperations transitOperations;
    private final VaultRequestIdCapture requestIdCapture;
    private final VaultRetryExecutor retryExecutor;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    VaultSecretClient(
            VaultSecretConfigurationResolver.ResolvedVaultConfiguration resolved,
            BootstrapSecretProperties bootstrapProperties,
            VaultOperations vaultOperations,
            Runnable closeAction) {
        this(resolved, bootstrapProperties, vaultOperations, new VaultRequestIdCapture(), closeAction);
    }

    VaultSecretClient(
            VaultSecretConfigurationResolver.ResolvedVaultConfiguration resolved,
            BootstrapSecretProperties bootstrapProperties,
            VaultOperations vaultOperations,
            VaultRequestIdCapture requestIdCapture,
            Runnable closeAction) {
        this.providerId = resolved.providerId();
        this.configurationHash = resolved.configurationHash();
        this.properties = resolved.properties();
        this.bootstrapProperties = bootstrapProperties;
        this.kvOperations = vaultOperations.opsForVersionedKeyValue(properties.getKv().getMount());
        this.transitOperations = vaultOperations.opsForTransit(properties.getTransit().getMount());
        this.requestIdCapture = requestIdCapture == null ? new VaultRequestIdCapture() : requestIdCapture;
        this.retryExecutor = new VaultRetryExecutor(bootstrapProperties.getResilience().getMaxAttempts());
        this.closeAction = closeAction == null ? () -> { } : closeAction;
    }

    @Override
    public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> versions = new ArrayList<>();
        List<String> requestIds = new ArrayList<>();
        for (String logicalPath : request.logicalPaths()) {
            requestIdCapture.clear();
            Versioned<Map<String, Object>> versioned = readVersion(logicalPath, Versioned.Version.unversioned());
            String requestId = requestIdCapture.consume();
            if (requestId != null) {
                requestIds.add(requestId);
            }
            if (versioned == null || !versioned.hasData()) {
                if (bootstrapProperties.getPropertySource().getMissingPolicy()
                        == BootstrapSecretProperties.MissingPolicy.LOCAL) {
                    log.warn("Vault bootstrap Secret path is missing; retaining local value: path={}, requestId={}",
                            logicalPath, requestId);
                    continue;
                }
                throw new SecretBootstrapException(
                        "SEC-STORE-001",
                        "Vault bootstrap Secret Bundle is missing");
            }
            Map<String, Object> flattened = flatten(versioned.getRequiredData());
            mergeWithoutAmbiguity(merged, flattened);
            versions.add(logicalPath + "@" + versionValue(versioned));
        }
        return new SecretBootstrapResult(
                providerId,
                digestVersions(versions),
                String.join(",", request.logicalPaths()),
                Instant.now(),
                merged,
                requestIds.isEmpty() ? null : String.join(",", requestIds));
    }

    @Override
    public SecretValue read(SecretReference reference) {
        ensureOpen();
        ResolvedSecret resolved = resolveSecret(reference);
        Versioned<Map<String, Object>> versioned = readVersion(resolved.path(), resolveVersion(reference.version()));
        if (versioned == null || !versioned.hasData()) {
            throw new SecretException("SEC-STORE-001", "Secret is missing: " + reference.logicalName());
        }
        Object value = selectValue(versioned.getRequiredData(), resolved.field(), reference.logicalName());
        byte[] bytes = toBytes(value, reference.logicalName());
        try {
            return DestroyableSecretValue.ofBytes(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public SecretMetadata metadata(SecretReference reference) {
        ensureOpen();
        ResolvedSecret resolved = resolveSecret(reference);
        Versioned<Map<String, Object>> versioned = readVersion(resolved.path(), resolveVersion(reference.version()));
        if (versioned == null || !versioned.hasMetadata()) {
            throw new SecretException("SEC-STORE-001", "Secret metadata is missing: " + reference.logicalName());
        }
        Versioned.Metadata metadata = versioned.getRequiredMetadata();
        return new SecretMetadata(
                versionValue(versioned),
                metadata.getCreatedAt(),
                null,
                Map.of("provider", "vault"));
    }

    @Override
    public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
        ensureOpen();
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        }
        String physicalKey = physicalKey(keyReference);
        VaultTransitContext transitContext = transitContext(keyReference.version(), true);
        try {
            String ciphertext = retryExecutor.execute(
                    () -> transitOperations.encrypt(physicalKey, plaintextKey, transitContext));
            return new WrappedKey(ciphertext.getBytes(StandardCharsets.UTF_8), WRAPPING_ALGORITHM);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mapProviderFailure("wrap", exception);
        }
    }

    private SecretException mapProviderFailure(String operation, RuntimeException exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException response) {
                String code = switch (response.getStatusCode().value()) {
                    case 401 -> "SEC-AUTH-001";
                    case 403 -> "SEC-AUTHZ-001";
                    case 409, 410 -> "SEC-PROVIDER-002";
                    default -> "SEC-PROVIDER-001";
                };
                return new SecretException(code, "Vault " + operation + " request failed", exception);
            }
            current = current.getCause();
        }
        return new SecretException("SEC-PROVIDER-001", "Vault " + operation + " request failed", exception);
    }

    private SecretCryptoException mapCryptoProviderFailure(String operation, RuntimeException exception, String code) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof RestClientResponseException response
                    && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)) {
                return new SecretCryptoException(response.getStatusCode().value() == 401 ? "SEC-AUTH-001" : "SEC-AUTHZ-001",
                        "Vault " + operation + " request was rejected", exception);
            }
            current = current.getCause();
        }
        return new SecretCryptoException(code, "Vault " + operation + " failed", exception);
    }

    @Override
    public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
        ensureOpen();
        if (!WRAPPING_ALGORITHM.equals(wrappedKey.algorithm())) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped key algorithm is not supported by Vault");
        }
        String physicalKey = physicalKey(keyReference);
        byte[] encoded = wrappedKey.value();
        try {
            String ciphertext = new String(encoded, StandardCharsets.UTF_8);
            if (!ciphertext.startsWith("vault:v")) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped key is not a Vault Transit ciphertext");
            }
            VaultTransitContext transitContext = transitContext("current", false);
            return retryExecutor.execute(() -> transitOperations.decrypt(
                    physicalKey,
                    ciphertext,
                    transitContext));
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mapCryptoProviderFailure("unwrap logical key " + keyReference.logicalKey(), exception, "SEC-CRYPTO-002");
        } finally {
            Arrays.fill(encoded, (byte) 0);
        }
    }

    @Override
    public SignatureValue sign(KeyReference keyReference, byte[] payload, CryptoContext context) {
        ensureOpen();
        validateSigningKey(keyReference);
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-001", "Signing payload must not be empty");
        }
        byte[] copy = payload.clone();
        try {
            String physicalKey = physicalKey(keyReference);
            Plaintext plaintext = Plaintext.of(copy).with(transitContext(keyReference.version(), true));
            String signature = retryExecutor.execute(() -> transitOperations
                    .sign(physicalKey, plaintext)
                    .getSignature());
            return new SignatureValue(signature);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mapCryptoProviderFailure("sign logical key " + keyReference.logicalKey(), exception, "SEC-SIGN-001");
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    @Override
    public boolean verify(
            KeyReference keyReference,
            byte[] payload,
            SignatureValue signature,
            CryptoContext context) {
        ensureOpen();
        validateSigningKey(keyReference);
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-002", "Verification payload must not be empty");
        }
        if (signature == null) {
            throw new SecretCryptoException("SEC-SIGN-002", "Signature must not be null");
        }
        byte[] copy = payload.clone();
        try {
            String physicalKey = physicalKey(keyReference);
            Plaintext plaintext = Plaintext.of(copy).with(transitContext(keyReference.version(), false));
            return retryExecutor.execute(() -> transitOperations.verify(
                    physicalKey, plaintext, Signature.of(signature.value())));
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mapCryptoProviderFailure("verify logical key " + keyReference.logicalKey(), exception, "SEC-SIGN-002");
        } finally {
            Arrays.fill(copy, (byte) 0);
        }
    }

    @Override
    public SecretProviderDescriptor descriptor() {
        return new SecretProviderDescriptor("vault", providerId, CAPABILITIES);
    }

    @Override
    public java.util.Optional<SecretBackend> secretBackend() {
        return java.util.Optional.of(this);
    }

    @Override
    public java.util.Optional<KeyWrappingBackend> keyWrappingBackend() {
        return java.util.Optional.of(this);
    }

    @Override
    public java.util.Optional<SigningBackend> signingBackend() {
        return java.util.Optional.of(this);
    }

    String configurationHash() {
        return configurationHash;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closeAction.run();
        }
    }

    private Versioned<Map<String, Object>> readVersion(String path, Versioned.Version version) {
        try {
            return retryExecutor.execute(() -> kvOperations.get(path, version, mapType()));
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw mapProviderFailure("Secret read", exception);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Class<Map<String, Object>> mapType() {
        return (Class) Map.class;
    }

    private ResolvedSecret resolveSecret(SecretReference reference) {
        VaultSecretProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        String path;
        String configuredField = null;
        if (mapping == null) {
            BootstrapSecretProperties.PropertySource source = bootstrapProperties.getPropertySource();
            path = "atlas-richie/" + source.getEnvironment() + "/" + source.getApplication() + "/"
                    + properties.getKv().getRuntimePrefix() + "/" + reference.logicalName();
        } else {
            path = mapping.getPath();
            configuredField = mapping.getField();
        }
        return new ResolvedSecret(path, reference.field() == null ? configuredField : reference.field());
    }

    private Versioned.Version resolveVersion(SecretVersionSelector selector) {
        if (selector.type() == SecretVersionSelector.Type.LATEST) {
            return Versioned.Version.unversioned();
        }
        if (selector.type() != SecretVersionSelector.Type.VERSION) {
            throw new SecretConfigurationException(
                    "SEC-STORE-002",
                    "Vault KV v2 supports latest or a numeric version selector");
        }
        try {
            int version = Integer.parseInt(selector.value());
            if (version <= 0) {
                throw new NumberFormatException("version must be positive");
            }
            return Versioned.Version.from(version);
        } catch (NumberFormatException exception) {
            throw new SecretConfigurationException(
                    "SEC-STORE-002",
                    "Vault KV v2 version must be a positive integer",
                    exception);
        }
    }

    private String physicalKey(KeyReference reference) {
        String physicalKey = properties.getTransit().getKeyBindings().get(reference.logicalKey());
        if (physicalKey == null || physicalKey.isBlank()) {
            throw new SecretConfigurationException(
                    "SEC-KEY-001",
                    "No Vault Transit key binding exists for logical key " + reference.logicalKey());
        }
        return physicalKey;
    }

    private void validateSigningKey(KeyReference reference) {
        if (reference == null || reference.purpose() != cn.richie696.component.secret.api.crypto.KeyPurpose.SIGNING) {
            throw new SecretConfigurationException(
                    "SEC-KEY-003", "Signing requires a KeyReference with SIGNING purpose");
        }
    }

    private VaultTransitContext transitContext(String version, boolean includeVersion) {
        // CryptoContext 的 AAD 已由 Core 的 AES-GCM 层认证。Vault Transit 的 context
        // 仅适用于 derived key，默认 Key 不应被强制要求开启 derived 模式。
        VaultTransitContext.VaultTransitRequestBuilder builder = VaultTransitContext.builder();
        if (includeVersion && version != null && !"current".equalsIgnoreCase(version)) {
            try {
                int keyVersion = Integer.parseInt(version);
                if (keyVersion <= 0) {
                    throw new NumberFormatException("key version must be positive");
                }
                builder.keyVersion(keyVersion);
            } catch (NumberFormatException exception) {
                throw new SecretConfigurationException(
                        "SEC-KEY-002",
                        "Vault Transit key version must be current or a positive integer",
                        exception);
            }
        }
        return builder.build();
    }

    private Object selectValue(Map<String, Object> data, String field, String logicalName) {
        if (field != null) {
            if (!data.containsKey(field)) {
                throw new SecretException("SEC-STORE-001", "Secret field is missing: " + logicalName);
            }
            return data.get(field);
        }
        if (data.containsKey("value")) {
            return data.get("value");
        }
        if (data.size() == 1) {
            return data.values().iterator().next();
        }
        throw new SecretConfigurationException(
                "SEC-STORE-003",
                "Secret contains multiple fields; configure a field mapping for " + logicalName);
    }

    private byte[] toBytes(Object value, String logicalName) {
        if (value instanceof byte[] bytes) {
            return bytes.clone();
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return value.toString().getBytes(StandardCharsets.UTF_8);
        }
        throw new SecretConfigurationException(
                "SEC-STORE-003",
                "Secret field is not a scalar value: " + logicalName);
    }

    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> target = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            flattenValue(target, entry.getKey(), entry.getValue());
        }
        return target;
    }

    private void flattenValue(Map<String, Object> target, String path, Object value) {
        if (value == null) {
            throw new SecretBootstrapException("SEC-STORE-003", "Vault Bundle contains a null value");
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                flattenValue(target, path + "." + key, entry.getValue());
            }
            return;
        }
        if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                flattenValue(target, path + "[" + index + "]", list.get(index));
            }
            return;
        }
        if (target.putIfAbsent(path, value) != null) {
            throw new SecretBootstrapException("SEC-STORE-003", "Vault Bundle contains ambiguous properties");
        }
    }

    private void mergeWithoutAmbiguity(Map<String, Object> target, Map<String, Object> incoming) {
        for (Map.Entry<String, Object> entry : incoming.entrySet()) {
            Object existing = target.putIfAbsent(entry.getKey(), entry.getValue());
            if (existing != null && !java.util.Objects.deepEquals(existing, entry.getValue())) {
                throw new SecretBootstrapException(
                        "SEC-STORE-003",
                        "Vault Bundles define conflicting values for " + entry.getKey());
            }
        }
    }

    private String digestVersions(List<String> versions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String version : versions) {
                digest.update(version.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private String versionValue(Versioned<?> versioned) {
        return versioned.getVersion().isVersioned()
                ? Integer.toString(versioned.getVersion().getVersion())
                : "latest";
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Vault Secret Provider session is closed");
        }
    }

    private record ResolvedSecret(String path, String field) {
    }
}
