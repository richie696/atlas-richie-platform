/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

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
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.api.provider.SecretProviderDescriptor;
import cn.richie696.component.secret.api.provider.SecretProviderSession;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapClient;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapResult;
import cn.richie696.component.secret.core.DestroyableSecretValue;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.aliyun.tea.TeaException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** 阿里云 KMS Secret Manager + KMS 的线程安全 Provider 会话。 */
public final class AliyunSecretClient implements
        SecretBootstrapClient, SecretBackend, KeyWrappingBackend, SecretProviderSession {
    private static final String WRAPPING_ALGORITHM = "aliyun-kms-symmetric-default";
    private static final Set<String> SECRET_NOT_FOUND_CODES = Set.of(
            "Forbidden.ResourceNotFound");
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ, SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);

    private final String providerId;
    private final String configurationHash;
    private final AliyunSecretProperties properties;
    private final BootstrapSecretProperties bootstrap;
    private final AliyunKmsGateway gateway;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    AliyunSecretClient(
            AliyunSecretConfigurationResolver.ResolvedAliyunConfiguration resolved,
            BootstrapSecretProperties bootstrap,
            AliyunKmsGateway gateway,
            Runnable closeAction) {
        this.providerId = resolved.providerId();
        this.configurationHash = resolved.configurationHash();
        this.properties = resolved.properties();
        this.bootstrap = bootstrap;
        this.gateway = gateway;
        this.closeAction = closeAction == null ? () -> { } : closeAction;
    }

    @Override
    public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> versions = new ArrayList<>();
        String requestId = null;
        for (String path : request.logicalPaths()) {
            GetSecretValueResponseBody body = getSecret(secretName(path), null);
            if (body == null) {
                if (bootstrap.getPropertySource().getMissingPolicy()
                        == BootstrapSecretProperties.MissingPolicy.LOCAL) continue;
                throw new SecretBootstrapException("SEC-STORE-001", "Alibaba Cloud bootstrap Secret Bundle is missing");
            }
            mergeWithoutAmbiguity(merged, flatten(parseObject(body)));
            versions.add(path + "@" + version(body));
            requestId = body.getRequestId();
        }
        return new SecretBootstrapResult(
                providerId, digestVersions(versions), String.join(",", request.logicalPaths()),
                Instant.now(), merged, requestId);
    }

    @Override
    public SecretValue read(SecretReference reference) {
        ensureOpen();
        ResolvedSecret resolved = resolveSecret(reference);
        GetSecretValueResponseBody body = getSecret(resolved.secretName(), reference.version());
        if (body == null) throw new SecretException("SEC-STORE-001", "Secret is missing: " + reference.logicalName());
        byte[] bytes = extractValue(body, resolved.field(), reference.logicalName());
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
        GetSecretValueResponseBody body = getSecret(resolved.secretName(), reference.version());
        if (body == null) throw new SecretException("SEC-STORE-001", "Secret metadata is missing: " + reference.logicalName());
        return new SecretMetadata(
                version(body), parseInstant(body.getCreateTime()), null,
                Map.of("provider", "aliyun", "stages", String.join(",", versionStages(body))));
    }

    @Override
    public WrappedKey wrap(KeyReference reference, byte[] plaintextKey, CryptoContext context) {
        ensureOpen();
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        }
        try {
            var response = gateway.encrypt(new EncryptRequest()
                    .setKeyId(physicalKey(reference))
                    .setPlaintext(Base64.getEncoder().encodeToString(plaintextKey))
                    .setEncryptionContext(encryptionContext(reference, context)));
            if (response == null || response.getBody() == null
                    || response.getBody().getCiphertextBlob() == null) {
                throw new SecretCryptoException("SEC-CRYPTO-001", "Alibaba Cloud KMS returned no ciphertext");
            }
            return new WrappedKey(
                    response.getBody().getCiphertextBlob().getBytes(StandardCharsets.UTF_8), WRAPPING_ALGORITHM);
        } catch (SecretException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-001", "Alibaba Cloud KMS failed to wrap logical key " + reference.logicalKey(), exception);
        }
    }

    @Override
    public byte[] unwrap(KeyReference reference, WrappedKey wrappedKey, CryptoContext context) {
        ensureOpen();
        if (!WRAPPING_ALGORITHM.equals(wrappedKey.algorithm())) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped key algorithm is not supported by Alibaba Cloud KMS");
        }
        String expectedKey = physicalKey(reference);
        byte[] ciphertext = wrappedKey.value();
        try {
            var response = gateway.decrypt(new DecryptRequest()
                    .setCiphertextBlob(new String(ciphertext, StandardCharsets.UTF_8))
                    .setEncryptionContext(encryptionContext(reference, context)));
            if (response == null || response.getBody() == null || response.getBody().getPlaintext() == null) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "Alibaba Cloud KMS returned no plaintext");
            }
            if (response.getBody().getKeyId() != null && !expectedKey.equals(response.getBody().getKeyId())) {
                throw new SecretCryptoException("SEC-CRYPTO-002", "Alibaba Cloud KMS returned an unexpected key identity");
            }
            return Base64.getDecoder().decode(response.getBody().getPlaintext());
        } catch (SecretException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-002", "Alibaba Cloud KMS failed to unwrap logical key " + reference.logicalKey(), exception);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    @Override public SecretProviderDescriptor descriptor() {
        return new SecretProviderDescriptor("aliyun", providerId, CAPABILITIES);
    }
    @Override public java.util.Optional<SecretBackend> secretBackend() { return java.util.Optional.of(this); }
    @Override public java.util.Optional<KeyWrappingBackend> keyWrappingBackend() { return java.util.Optional.of(this); }
    String configurationHash() { return configurationHash; }
    @Override public void close() { if (closed.compareAndSet(false, true)) closeAction.run(); }

    private GetSecretValueResponseBody getSecret(String secretName, SecretVersionSelector selector) {
        try {
            GetSecretValueRequest request = new GetSecretValueRequest().setSecretName(secretName);
            if (selector != null) {
                switch (selector.type()) {
                    case LATEST -> { }
                    case VERSION -> request.setVersionId(selector.value());
                    case STAGE, ALIAS -> request.setVersionStage(selector.value());
                }
            }
            var response = gateway.getSecretValue(request);
            return response == null ? null : response.getBody();
        } catch (TeaException exception) {
            String code = exception.getCode();
            if (code != null && SECRET_NOT_FOUND_CODES.contains(code.trim())) return null;
            throw new SecretException("SEC-PROVIDER-001", "Alibaba Cloud Secret Manager read failed", exception);
        } catch (Exception exception) {
            throw new SecretException("SEC-PROVIDER-001", "Alibaba Cloud Secret Manager read failed", exception);
        }
    }

    private ResolvedSecret resolveSecret(SecretReference reference) {
        AliyunSecretProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        if (mapping != null) {
            return new ResolvedSecret(
                    mapping.getSecretName(), reference.field() == null ? mapping.getField() : reference.field());
        }
        var source = bootstrap.getPropertySource();
        return new ResolvedSecret(secretName("atlas-richie/" + source.getEnvironment() + "/"
                + source.getApplication() + "/runtime/" + reference.logicalName()), reference.field());
    }
    private String secretName(String logicalPath) {
        String prefix = properties.getSecretsManager().getPathPrefix();
        return prefix == null || prefix.isBlank() ? logicalPath : prefix + "/" + logicalPath;
    }
    private String physicalKey(KeyReference reference) {
        String key = properties.getKms().getKeyBindings().get(reference.logicalKey());
        if (key == null || key.isBlank()) {
            throw new SecretConfigurationException(
                    "SEC-KEY-001", "No Alibaba Cloud KMS key binding exists for logical key " + reference.logicalKey());
        }
        return key;
    }
    private Map<String, String> encryptionContext(KeyReference reference, CryptoContext context) {
        byte[] aad = context == null ? new byte[0] : context.associatedData();
        try {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("atlas-component", "secret-envelope");
            result.put("atlas-aad-sha256", sha256(aad));
            result.put("atlas-key", reference.logicalKey());
            result.put("atlas-version", reference.version());
            result.put("atlas-purpose", reference.purpose().name());
            if (context != null) {
                copyEnvelopeAttribute(context, result, "atlas.secret.envelope-version", "atlas-envelope-version");
                copyEnvelopeAttribute(context, result, "atlas.secret.algorithm", "atlas-algorithm");
                copyEnvelopeAttribute(context, result, "atlas.secret.nonce-sha256", "atlas-nonce-sha256");
            }
            return Map.copyOf(result);
        } finally {
            Arrays.fill(aad, (byte) 0);
        }
    }

    private void copyEnvelopeAttribute(
            CryptoContext context,
            Map<String, String> target,
            String source,
            String destination) {
        String value = context.attributes().get(source);
        if (value != null && !value.isBlank()) {
            target.put(destination, value);
        }
    }

    private byte[] extractValue(GetSecretValueResponseBody body, String field, String logicalName) {
        if (body.getSecretData() == null) throw new SecretException("SEC-STORE-001", "Secret has no value: " + logicalName);
        if (field == null) {
            return "binary".equalsIgnoreCase(body.getSecretDataType())
                    ? Base64.getDecoder().decode(body.getSecretData())
                    : body.getSecretData().getBytes(StandardCharsets.UTF_8);
        }
        Object value = parseObject(body).get(field);
        if (value == null) throw new SecretException("SEC-STORE-001", "Secret field is missing: " + logicalName);
        if (!(value instanceof CharSequence || value instanceof Number || value instanceof Boolean)) {
            throw new SecretConfigurationException("SEC-STORE-003", "Secret field is not scalar: " + logicalName);
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    private Map<String, Object> parseObject(GetSecretValueResponseBody body) {
        try {
            byte[] json = "binary".equalsIgnoreCase(body.getSecretDataType())
                    ? Base64.getDecoder().decode(body.getSecretData())
                    : body.getSecretData().getBytes(StandardCharsets.UTF_8);
            try {
                return objectMapper.readValue(json, new TypeReference<>() { });
            } finally {
                Arrays.fill(json, (byte) 0);
            }
        } catch (Exception exception) {
            throw new SecretConfigurationException("SEC-STORE-003", "Alibaba Cloud Secret must contain a JSON object", exception);
        }
    }
    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> flattenValue(result, key, value));
        return result;
    }
    private void flattenValue(Map<String, Object> target, String path, Object value) {
        if (value == null) throw new SecretBootstrapException("SEC-STORE-003", "Alibaba Cloud Bundle contains null");
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> flattenValue(target, path + "." + key, nested));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) flattenValue(target, path + "[" + i + "]", list.get(i));
        } else if (target.putIfAbsent(path, value) != null) {
            throw new SecretBootstrapException("SEC-STORE-003", "Alibaba Cloud Bundle contains ambiguous properties");
        }
    }
    private void mergeWithoutAmbiguity(Map<String, Object> target, Map<String, Object> incoming) {
        incoming.forEach((key, value) -> {
            Object existing = target.putIfAbsent(key, value);
            if (existing != null && !java.util.Objects.deepEquals(existing, value)) {
                throw new SecretBootstrapException("SEC-STORE-003", "Alibaba Cloud Bundles conflict for " + key);
            }
        });
    }
    private List<String> versionStages(GetSecretValueResponseBody body) {
        return body.getVersionStages() == null || body.getVersionStages().getVersionStage() == null
                ? List.of() : body.getVersionStages().getVersionStage();
    }
    private String version(GetSecretValueResponseBody body) {
        return body.getVersionId() == null || body.getVersionId().isBlank() ? "current" : body.getVersionId();
    }
    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Instant.parse(value); } catch (DateTimeParseException ignored) { return null; }
    }
    private String digestVersions(List<String> versions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            versions.forEach(value -> { digest.update(value.getBytes(StandardCharsets.UTF_8)); digest.update((byte) 0); });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Alibaba Cloud Secret Provider session is closed");
    }
    private record ResolvedSecret(String secretName, String field) { }
}
