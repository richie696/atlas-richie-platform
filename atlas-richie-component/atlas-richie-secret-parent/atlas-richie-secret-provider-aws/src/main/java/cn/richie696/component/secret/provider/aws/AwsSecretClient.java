/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.MessageType;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.VerifyRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;

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

/** AWS Secrets Manager + KMS 的线程安全 Provider 会话。 */
public final class AwsSecretClient implements
        SecretBootstrapClient, SecretBackend, KeyWrappingBackend, SigningBackend, SecretProviderSession {
    private static final String WRAPPING_ALGORITHM = "aws-kms-symmetric-default";
    private static final Set<SecretCapability> CAPABILITIES = Set.of(
            SecretCapability.SECRET_READ,
            SecretCapability.SECRET_VERSIONING,
            SecretCapability.KEY_WRAP,
            SecretCapability.KEY_UNWRAP,
            SecretCapability.SIGN,
            SecretCapability.VERIFY);

    private final String providerId;
    private final String configurationHash;
    private final AwsSecretProperties properties;
    private final BootstrapSecretProperties bootstrapProperties;
    private final SecretsManagerClient secrets;
    private final KmsClient kms;
    private final ObjectMapper objectMapper;
    private final Runnable closeAction;
    private final AtomicBoolean closed = new AtomicBoolean();

    AwsSecretClient(
            AwsSecretConfigurationResolver.ResolvedAwsConfiguration resolved,
            BootstrapSecretProperties bootstrapProperties,
            SecretsManagerClient secrets,
            KmsClient kms,
            ObjectMapper objectMapper,
            Runnable closeAction) {
        this.providerId = resolved.providerId();
        this.configurationHash = resolved.configurationHash();
        this.properties = resolved.properties();
        this.bootstrapProperties = bootstrapProperties;
        this.secrets = secrets;
        this.kms = kms;
        this.objectMapper = objectMapper;
        this.closeAction = closeAction == null ? () -> { } : closeAction;
    }

    @Override
    public SecretBootstrapResult load(SecretBootstrapRequest request) {
        ensureOpen();
        Map<String, Object> merged = new LinkedHashMap<>();
        List<String> versions = new ArrayList<>();
        String requestId = null;
        for (String path : request.logicalPaths()) {
            GetSecretValueResponse response = getSecret(secretId(path), null);
            if (response == null) {
                if (bootstrapProperties.getPropertySource().getMissingPolicy()
                        == BootstrapSecretProperties.MissingPolicy.LOCAL) continue;
                throw new SecretBootstrapException("SEC-STORE-001", "AWS bootstrap Secret Bundle is missing");
            }
            mergeWithoutAmbiguity(merged, flatten(parseObject(response)));
            versions.add(path + "@" + version(response));
            if (response.responseMetadata() != null) {
                requestId = response.responseMetadata().requestId();
            }
        }
        return new SecretBootstrapResult(
                providerId,
                digestVersions(versions),
                String.join(",", request.logicalPaths()),
                Instant.now(),
                merged,
                requestId);
    }

    @Override
    public SecretValue read(SecretReference reference) {
        ensureOpen();
        ResolvedSecret resolved = resolveSecret(reference);
        GetSecretValueResponse response = getSecret(resolved.secretId(), reference.version());
        if (response == null) {
            throw new SecretException("SEC-STORE-001", "Secret is missing: " + reference.logicalName());
        }
        byte[] bytes = extractValue(response, resolved.field(), reference.logicalName());
        try {
            return DestroyableSecretValue.ofBytes(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public SecretMetadata metadata(SecretReference reference) {
        ResolvedSecret resolved = resolveSecret(reference);
        GetSecretValueResponse response = getSecret(resolved.secretId(), reference.version());
        if (response == null) {
            throw new SecretException("SEC-STORE-001", "Secret metadata is missing: " + reference.logicalName());
        }
        return new SecretMetadata(
                version(response),
                response.createdDate(),
                null,
                Map.of("provider", "aws", "stages", String.join(",", response.versionStages())));
    }

    @Override
    public WrappedKey wrap(KeyReference keyReference, byte[] plaintextKey, CryptoContext context) {
        ensureOpen();
        if (plaintextKey == null || plaintextKey.length == 0) {
            throw new SecretCryptoException("SEC-CRYPTO-001", "Plaintext data key must not be empty");
        }
        try {
            var response = kms.encrypt(EncryptRequest.builder()
                    .keyId(physicalKey(keyReference))
                    .plaintext(SdkBytes.fromByteArray(plaintextKey))
                    .encryptionContext(encryptionContext(context))
                    .build());
            return new WrappedKey(response.ciphertextBlob().asByteArray(), WRAPPING_ALGORITHM);
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-001", "AWS KMS failed to wrap logical key " + keyReference.logicalKey(), exception);
        }
    }

    @Override
    public byte[] unwrap(KeyReference keyReference, WrappedKey wrappedKey, CryptoContext context) {
        ensureOpen();
        if (!WRAPPING_ALGORITHM.equals(wrappedKey.algorithm())) {
            throw new SecretCryptoException("SEC-CRYPTO-002", "Wrapped key algorithm is not supported by AWS KMS");
        }
        byte[] ciphertext = wrappedKey.value();
        try {
            return kms.decrypt(DecryptRequest.builder()
                            .keyId(physicalKey(keyReference))
                            .ciphertextBlob(SdkBytes.fromByteArray(ciphertext))
                            .encryptionContext(encryptionContext(context))
                            .build())
                    .plaintext().asByteArray();
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException(
                    "SEC-CRYPTO-002", "AWS KMS failed to unwrap logical key " + keyReference.logicalKey(), exception);
        } finally {
            Arrays.fill(ciphertext, (byte) 0);
        }
    }

    @Override
    public SignatureValue sign(KeyReference keyReference, byte[] payload, CryptoContext context) {
        ensureOpen();
        validateSigningKey(keyReference);
        validatePayload(payload, "Signing");
        try {
            byte[] copy = payload.clone();
            try {
                byte[] signature = kms.sign(SignRequest.builder()
                        .keyId(physicalKey(keyReference))
                        .message(SdkBytes.fromByteArray(copy))
                        .messageType(MessageType.RAW)
                        .signingAlgorithm(signingAlgorithm())
                        .build()).signature().asByteArray();
                return new SignatureValue(encodeSignature(physicalKey(keyReference), signature));
            } finally {
                Arrays.fill(copy, (byte) 0);
            }
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-SIGN-001", "AWS KMS signing failed", exception);
        }
    }

    @Override
    public boolean verify(KeyReference keyReference, byte[] payload, SignatureValue signature, CryptoContext context) {
        ensureOpen();
        validateSigningKey(keyReference);
        validatePayload(payload, "Verification");
        if (signature == null) {
            throw new SecretCryptoException("SEC-SIGN-002", "Signature must not be null");
        }
        try {
            SignatureEnvelope envelope = decodeSignature(signature.value());
            String configuredKey = physicalKey(keyReference);
            if (!configuredKey.equals(envelope.keyId())) {
                throw new SecretCryptoException("SEC-SIGN-002", "Signature key does not match logical key binding");
            }
            byte[] copy = payload.clone();
            try {
                return kms.verify(VerifyRequest.builder()
                        .keyId(envelope.keyId())
                        .message(SdkBytes.fromByteArray(copy))
                        .messageType(MessageType.RAW)
                        .signature(SdkBytes.fromByteArray(envelope.signature()))
                        .signingAlgorithm(SigningAlgorithmSpec.fromValue(envelope.algorithm()))
                        .build()).signatureValid();
            } finally {
                Arrays.fill(copy, (byte) 0);
                Arrays.fill(envelope.signature(), (byte) 0);
            }
        } catch (SecretException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new SecretCryptoException("SEC-SIGN-002", "AWS KMS signature verification failed", exception);
        }
    }

    @Override public SecretProviderDescriptor descriptor() {
        return new SecretProviderDescriptor("aws", providerId, CAPABILITIES);
    }
    @Override public java.util.Optional<SecretBackend> secretBackend() { return java.util.Optional.of(this); }
    @Override public java.util.Optional<KeyWrappingBackend> keyWrappingBackend() { return java.util.Optional.of(this); }
    @Override public java.util.Optional<SigningBackend> signingBackend() { return java.util.Optional.of(this); }
    String configurationHash() { return configurationHash; }
    @Override public void close() { if (closed.compareAndSet(false, true)) closeAction.run(); }

    private GetSecretValueResponse getSecret(String secretId, SecretVersionSelector selector) {
        try {
            GetSecretValueRequest.Builder builder = GetSecretValueRequest.builder().secretId(secretId);
            if (selector != null) {
                switch (selector.type()) {
                    case LATEST -> { }
                    case VERSION -> builder.versionId(selector.value());
                    case STAGE, ALIAS -> builder.versionStage(selector.value());
                }
            }
            return secrets.getSecretValue(builder.build());
        } catch (ResourceNotFoundException exception) {
            return null;
        } catch (RuntimeException exception) {
            throw new SecretException("SEC-PROVIDER-001", "AWS Secrets Manager read failed", exception);
        }
    }

    private ResolvedSecret resolveSecret(SecretReference reference) {
        AwsSecretProperties.SecretMapping mapping = properties.getSecrets().get(reference.logicalName());
        if (mapping != null) {
            return new ResolvedSecret(
                    mapping.getSecretId(), reference.field() == null ? mapping.getField() : reference.field());
        }
        var source = bootstrapProperties.getPropertySource();
        String path = "atlas-richie/" + source.getEnvironment() + "/" + source.getApplication()
                + "/runtime/" + reference.logicalName();
        return new ResolvedSecret(secretId(path), reference.field());
    }

    private String secretId(String logicalPath) {
        String prefix = properties.getSecretsManager().getPathPrefix();
        return prefix == null || prefix.isBlank() ? logicalPath : prefix + "/" + logicalPath;
    }

    private String physicalKey(KeyReference reference) {
        String key = properties.getKms().getKeyBindings().get(reference.logicalKey());
        if (key == null || key.isBlank()) {
            throw new SecretConfigurationException(
                    "SEC-KEY-001", "No AWS KMS key binding exists for logical key " + reference.logicalKey());
        }
        return key;
    }

    private void validateSigningKey(KeyReference reference) {
        if (reference == null || reference.purpose() != cn.richie696.component.secret.api.crypto.KeyPurpose.SIGNING) {
            throw new SecretConfigurationException("SEC-KEY-003", "Signing requires a KeyReference with SIGNING purpose");
        }
    }

    private void validatePayload(byte[] payload, String operation) {
        if (payload == null || payload.length == 0) {
            throw new SecretCryptoException("SEC-SIGN-001", operation + " payload must not be empty");
        }
    }

    private SigningAlgorithmSpec signingAlgorithm() {
        return SigningAlgorithmSpec.fromValue(properties.getKms().getSigningAlgorithm());
    }

    private String encodeSignature(String keyId, byte[] signature) {
        try {
            return "aws-kms:v1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                    keyId.getBytes(StandardCharsets.UTF_8))
                    + ":" + signingAlgorithm().toString()
                    + ":" + java.util.Base64.getEncoder().encodeToString(signature);
        } finally {
            Arrays.fill(signature, (byte) 0);
        }
    }

    private SignatureEnvelope decodeSignature(String value) {
        String[] parts = value.split(":", 5);
        if (parts.length != 5 || !"aws-kms".equals(parts[0]) || !"v1".equals(parts[1])) {
            throw new SecretCryptoException("SEC-SIGN-002", "Signature is not an AWS KMS signature value");
        }
        try {
            String keyId = new String(java.util.Base64.getUrlDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            SigningAlgorithmSpec.fromValue(parts[3]);
            byte[] signature = java.util.Base64.getDecoder().decode(parts[4]);
            return new SignatureEnvelope(keyId, parts[3], signature);
        } catch (IllegalArgumentException exception) {
            throw new SecretCryptoException("SEC-SIGN-002", "AWS KMS signature value is malformed", exception);
        }
    }

    private record SignatureEnvelope(String keyId, String algorithm, byte[] signature) { }

    private Map<String, String> encryptionContext(CryptoContext context) {
        byte[] aad = context == null ? new byte[0] : context.associatedData();
        try {
            Map<String, String> result = new LinkedHashMap<>();
            result.put("atlas-component", "secret-envelope");
            result.put("atlas-aad-sha256", sha256(aad));
            if (context != null) {
                copyEnvelopeAttribute(context, result, "atlas.secret.key", "atlas-key");
                copyEnvelopeAttribute(context, result, "atlas.secret.version", "atlas-version");
                copyEnvelopeAttribute(context, result, "atlas.secret.purpose", "atlas-purpose");
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

    private byte[] extractValue(GetSecretValueResponse response, String field, String logicalName) {
        if (field == null) {
            if (response.secretBinary() != null) return response.secretBinary().asByteArray();
            if (response.secretString() == null) {
                throw new SecretException("SEC-STORE-001", "Secret has no value: " + logicalName);
            }
            return response.secretString().getBytes(StandardCharsets.UTF_8);
        }
        Object value = parseObject(response).get(field);
        if (value == null) throw new SecretException("SEC-STORE-001", "Secret field is missing: " + logicalName);
        if (!(value instanceof CharSequence || value instanceof Number || value instanceof Boolean)) {
            throw new SecretConfigurationException("SEC-STORE-003", "Secret field is not scalar: " + logicalName);
        }
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Map<String, Object> parseObject(GetSecretValueResponse response) {
        try {
            byte[] json = response.secretString() != null
                    ? response.secretString().getBytes(StandardCharsets.UTF_8)
                    : response.secretBinary().asByteArray();
            try {
                return objectMapper.readValue(json, new TypeReference<>() { });
            } finally {
                Arrays.fill(json, (byte) 0);
            }
        } catch (Exception exception) {
            throw new SecretConfigurationException("SEC-STORE-003", "AWS Secret must contain a JSON object", exception);
        }
    }

    private Map<String, Object> flatten(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> flattenValue(result, key, value));
        return result;
    }
    private void flattenValue(Map<String, Object> target, String path, Object value) {
        if (value == null) throw new SecretBootstrapException("SEC-STORE-003", "AWS Bundle contains a null value");
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, nested) -> flattenValue(target, path + "." + key, nested));
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) flattenValue(target, path + "[" + i + "]", list.get(i));
        } else if (target.putIfAbsent(path, value) != null) {
            throw new SecretBootstrapException("SEC-STORE-003", "AWS Bundle contains ambiguous properties");
        }
    }
    private void mergeWithoutAmbiguity(Map<String, Object> target, Map<String, Object> incoming) {
        incoming.forEach((key, value) -> {
            Object existing = target.putIfAbsent(key, value);
            if (existing != null && !java.util.Objects.deepEquals(existing, value)) {
                throw new SecretBootstrapException("SEC-STORE-003", "AWS Bundles conflict for " + key);
            }
        });
    }
    private String version(GetSecretValueResponse response) {
        return response.versionId() == null || response.versionId().isBlank() ? "current" : response.versionId();
    }
    private String digestVersions(List<String> versions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            versions.forEach(value -> {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
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
    private void ensureOpen() { if (closed.get()) throw new IllegalStateException("AWS Secret Provider session is closed"); }
    private record ResolvedSecret(String secretId, String field) { }
}
