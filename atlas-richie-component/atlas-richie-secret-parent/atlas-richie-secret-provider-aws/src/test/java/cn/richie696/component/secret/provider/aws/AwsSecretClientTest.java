/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.provider.aws;

import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.SignatureValue;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.VerifyRequest;
import software.amazon.awssdk.services.kms.model.VerifyResponse;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsSecretClientTest {
    private AwsTestBackend backend;
    private AwsSecretClient client;
    private AwsSecretProperties properties;
    private AtomicInteger closeCalls;

    @BeforeEach
    void setUp() {
        properties = new AwsSecretProperties();
        properties.setRegion("ap-southeast-1");
        AwsSecretProperties.SecretsManager secretsManager = new AwsSecretProperties.SecretsManager();
        secretsManager.setPathPrefix("company");
        properties.setSecretsManager(secretsManager);
        AwsSecretProperties.Kms kms = new AwsSecretProperties.Kms();
        kms.setKeyBindings(Map.of("default-envelope", "arn:aws:kms:ap-southeast-1:1:key/orders"));
        properties.setKms(kms);
        AwsSecretProperties.SecretMapping mapping = new AwsSecretProperties.SecretMapping();
        mapping.setSecretId("prod/orders/database");
        mapping.setField("password");
        properties.setSecrets(Map.of("database-password", mapping));

        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getPropertySource().setApplication("orders");
        bootstrap.getPropertySource().setEnvironment("prod");
        var resolved = new AwsSecretConfigurationResolver.ResolvedAwsConfiguration(
                "aws", properties, "configuration-hash");

        backend = new AwsTestBackend();
        closeCalls = new AtomicInteger();
        client = new AwsSecretClient(
                resolved,
                bootstrap,
                backend.secretsClient(),
                backend.kmsClient(),
                new ObjectMapper(),
                closeCalls::incrementAndGet);
    }

    @Test
    void mergesAndFlattensBootstrapBundlesBehindLogicalPaths() {
        backend.put("company/atlas-richie/prod/orders/common", response(
                "common-v3", "{\"platform\":{\"oauth\":{\"client-secret\":\"one\"}}}"));
        backend.put("company/atlas-richie/prod/orders/components", response(
                "components-v7", "{\"platform.component.storage.token\":\"two\"}"));

        var result = client.load(new SecretBootstrapRequest(
                "orders",
                "prod",
                List.of(
                        "atlas-richie/prod/orders/common",
                        "atlas-richie/prod/orders/components"),
                List.of()));

        assertThat(result.providerId()).isEqualTo("aws");
        assertThat(result.version()).hasSize(64);
        assertThat(result.values())
                .containsEntry("platform.oauth.client-secret", "one")
                .containsEntry("platform.component.storage.token", "two");
        assertThat(backend.lastSecretRequest.secretId())
                .isEqualTo("company/atlas-richie/prod/orders/components");
    }

    @Test
    void failsClosedWhenBootstrapBundlesConflict() {
        backend.put("company/one", response("v1", "{\"shared.key\":\"first\"}"));
        backend.put("company/two", response("v2", "{\"shared.key\":\"second\"}"));

        assertThatThrownBy(() -> client.load(new SecretBootstrapRequest(
                "orders", "prod", List.of("one", "two"), List.of())))
                .isInstanceOf(SecretBootstrapException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void resolvesLogicalSecretAndVersionStageInsideProvider() {
        backend.put("prod/orders/database", response(
                "db-v4", "{\"username\":\"orders\",\"password\":\"s3cret\"}"));
        SecretReference reference = new SecretReference(
                "database-password", SecretVersionSelector.stage("AWSPREVIOUS"), null);

        try (var value = client.read(reference)) {
            assertThat(value.copyChars()).containsExactly("s3cret".toCharArray());
        }
        assertThat(backend.lastSecretRequest.secretId()).isEqualTo("prod/orders/database");
        assertThat(backend.lastSecretRequest.versionStage()).isEqualTo("AWSPREVIOUS");
    }

    @Test
    void wrapsAndUnwrapsWithPhysicalKmsKnowledgeOnlyInsideProvider() {
        byte[] dataKey = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = "aws-kms-ciphertext".getBytes(StandardCharsets.UTF_8);
        backend.wrapped = ciphertext;
        backend.unwrapped = dataKey;
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of(
                "atlas.secret.key", "default-envelope",
                "atlas.secret.version", "current",
                "atlas.secret.envelope-version", "1",
                "atlas.secret.algorithm", "AES_256_GCM",
                "atlas.secret.nonce-sha256", "nonce-hash"));

        WrappedKey wrapped = client.wrap(
                KeyReference.envelopeEncryption("default-envelope"), dataKey, context);
        byte[] unwrapped = client.unwrap(
                KeyReference.envelopeEncryption("default-envelope"), wrapped, context);

        assertThat(wrapped.algorithm()).isEqualTo("aws-kms-symmetric-default");
        assertThat(unwrapped).isEqualTo(dataKey);
        assertThat(backend.lastEncryptRequest.keyId())
                .isEqualTo("arn:aws:kms:ap-southeast-1:1:key/orders");
        assertThat(backend.lastDecryptRequest.keyId())
                .isEqualTo("arn:aws:kms:ap-southeast-1:1:key/orders");
        assertThat(backend.lastEncryptRequest.encryptionContext())
                .isEqualTo(backend.lastDecryptRequest.encryptionContext())
                .containsEntry("atlas-component", "secret-envelope")
                .containsKey("atlas-aad-sha256")
                .containsEntry("atlas-key", "default-envelope")
                .containsEntry("atlas-version", "current")
                .containsEntry("atlas-algorithm", "AES_256_GCM")
                .containsEntry("atlas-nonce-sha256", "nonce-hash");
    }

    @Test
    void refusesUnmappedLogicalKeyInsteadOfAcceptingAnArnFromBusinessCode() {
        assertThatThrownBy(() -> client.wrap(
                KeyReference.envelopeEncryption("unknown-key"),
                new byte[32],
                CryptoContext.empty()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("logical key unknown-key");
    }

    @Test
    void verifiesAnOldSignatureOnlyWhenItsKeyRemainsInTheTrustedRotationWindow() {
        String oldKey = "arn:aws:kms:ap-southeast-1:1:key/oauth-old";
        String newKey = "arn:aws:kms:ap-southeast-1:1:key/oauth-new";
        properties.getKms().setKeyBindings(Map.of("oauth-signing", oldKey));
        byte[] payload = "oauth-token".getBytes(StandardCharsets.UTF_8);
        SignatureValue signature = client.sign(
                new KeyReference("oauth-signing", "current", KeyPurpose.SIGNING),
                payload,
                CryptoContext.empty());

        properties.getKms().setKeyBindings(Map.of("oauth-signing", newKey));
        properties.getKms().setVerificationKeyBindings(Map.of("oauth-signing", List.of(oldKey)));

        assertThat(client.verify(
                new KeyReference("oauth-signing", "current", KeyPurpose.SIGNING),
                payload,
                signature,
                CryptoContext.empty())).isTrue();
        assertThat(backend.lastVerifyRequest.keyId()).isEqualTo(oldKey);

        properties.getKms().setVerificationKeyBindings(Map.of());
        assertThatThrownBy(() -> client.verify(
                new KeyReference("oauth-signing", "current", KeyPurpose.SIGNING),
                payload,
                signature,
                CryptoContext.empty()))
                .isInstanceOf(cn.richie696.component.secret.api.exception.SecretCryptoException.class)
                .hasMessageContaining("not trusted");
    }

    @Test
    void closesOwnedResourcesExactlyOnceAndRejectsFurtherUse() {
        client.close();
        client.close();

        assertThat(closeCalls).hasValue(1);
        assertThatThrownBy(() -> client.read(SecretReference.latest("database-password")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("closed");
    }

    private GetSecretValueResponse response(String version, String value) {
        return GetSecretValueResponse.builder()
                .arn("arn:aws:secretsmanager:ap-southeast-1:1:secret:test")
                .name("test")
                .versionId(version)
                .versionStages("AWSCURRENT")
                .createdDate(Instant.parse("2026-08-22T00:00:00Z"))
                .secretString(value)
                .build();
    }

    private static final class AwsTestBackend {
        private final Map<String, GetSecretValueResponse> values = new LinkedHashMap<>();
        private GetSecretValueRequest lastSecretRequest;
        private EncryptRequest lastEncryptRequest;
        private DecryptRequest lastDecryptRequest;
        private SignRequest lastSignRequest;
        private VerifyRequest lastVerifyRequest;
        private byte[] wrapped;
        private byte[] unwrapped;

        void put(String secretId, GetSecretValueResponse value) {
            values.put(secretId, value);
        }

        SecretsManagerClient secretsClient() {
            return proxy(SecretsManagerClient.class, (method, arguments) -> {
                if ("getSecretValue".equals(method.getName())) {
                    lastSecretRequest = (GetSecretValueRequest) arguments[0];
                    return values.get(lastSecretRequest.secretId());
                }
                return defaultValue(method.getReturnType());
            });
        }

        KmsClient kmsClient() {
            return proxy(KmsClient.class, (method, arguments) -> {
                if ("encrypt".equals(method.getName())) {
                    lastEncryptRequest = (EncryptRequest) arguments[0];
                    return EncryptResponse.builder()
                            .keyId(lastEncryptRequest.keyId())
                            .ciphertextBlob(SdkBytes.fromByteArray(wrapped))
                            .build();
                }
                if ("decrypt".equals(method.getName())) {
                    lastDecryptRequest = (DecryptRequest) arguments[0];
                    return DecryptResponse.builder()
                            .keyId(lastDecryptRequest.keyId())
                            .plaintext(SdkBytes.fromByteArray(unwrapped))
                            .build();
                }
                if ("sign".equals(method.getName())) {
                    lastSignRequest = (SignRequest) arguments[0];
                    return SignResponse.builder()
                            .keyId(lastSignRequest.keyId())
                            .signature(SdkBytes.fromUtf8String("signature"))
                            .signingAlgorithm(lastSignRequest.signingAlgorithm())
                            .build();
                }
                if ("verify".equals(method.getName())) {
                    lastVerifyRequest = (VerifyRequest) arguments[0];
                    return VerifyResponse.builder()
                            .keyId(lastVerifyRequest.keyId())
                            .signatureValid(true)
                            .signingAlgorithm(lastVerifyRequest.signingAlgorithm())
                            .build();
                }
                return defaultValue(method.getReturnType());
            });
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive() || type == void.class) return null;
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            return 0;
        }

        @SuppressWarnings("unchecked")
        private static <T> T proxy(Class<T> type, Invocation invocation) {
            return (T) Proxy.newProxyInstance(
                    type.getClassLoader(),
                    new Class<?>[]{type},
                    (proxy, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "toString" -> type.getSimpleName() + "TestProxy";
                                case "hashCode" -> System.identityHashCode(proxy);
                                case "equals" -> proxy == arguments[0];
                                default -> null;
                            };
                        }
                        return invocation.invoke(method, arguments == null ? new Object[0] : arguments);
                    });
        }

        @FunctionalInterface
        private interface Invocation {
            Object invoke(Method method, Object[] arguments);
        }
    }
}
