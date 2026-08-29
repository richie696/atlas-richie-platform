/* Copyright (c) 2026 Richie (https://www.github.com/richie696) */
package cn.richie696.component.secret.provider.aliyun;

import cn.richie696.component.secret.api.SecretReference;
import cn.richie696.component.secret.api.SecretVersionSelector;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretBootstrapException;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.DecryptResponse;
import com.aliyun.kms20160120.models.DecryptResponseBody;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.EncryptResponse;
import com.aliyun.kms20160120.models.EncryptResponseBody;
import com.aliyun.kms20160120.models.GetSecretValueRequest;
import com.aliyun.kms20160120.models.GetSecretValueResponse;
import com.aliyun.kms20160120.models.GetSecretValueResponseBody;
import com.aliyun.tea.TeaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AliyunSecretClientTest {
    private FakeGateway gateway;
    private AliyunSecretClient client;
    private AtomicInteger closeCalls;

    @BeforeEach
    void setUp() {
        AliyunSecretProperties properties = new AliyunSecretProperties();
        properties.setRegion("cn-hangzhou");
        AliyunSecretProperties.SecretsManager secretsManager = new AliyunSecretProperties.SecretsManager();
        secretsManager.setPathPrefix("company");
        properties.setSecretsManager(secretsManager);
        AliyunSecretProperties.Kms kms = new AliyunSecretProperties.Kms();
        kms.setKeyBindings(Map.of("default-envelope", "alias/orders"));
        properties.setKms(kms);
        AliyunSecretProperties.SecretMapping mapping = new AliyunSecretProperties.SecretMapping();
        mapping.setSecretName("prod/orders/database");
        mapping.setField("password");
        properties.setSecrets(Map.of("database-password", mapping));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getPropertySource().setApplication("orders");
        bootstrap.getPropertySource().setEnvironment("prod");

        gateway = new FakeGateway();
        closeCalls = new AtomicInteger();
        client = new AliyunSecretClient(
                new AliyunSecretConfigurationResolver.ResolvedAliyunConfiguration(
                        "aliyun", properties, "configuration-hash"),
                bootstrap, gateway, closeCalls::incrementAndGet);
    }

    @Test
    void mergesAndFlattensBootstrapBundlesUsingPhysicalNamesInternally() {
        gateway.put("company/atlas-richie/prod/orders/common",
                response("v3", "{\"platform\":{\"oauth\":{\"client-secret\":\"one\"}}}"));
        gateway.put("company/atlas-richie/prod/orders/components",
                response("v7", "{\"platform.component.storage.token\":\"two\"}"));

        var result = client.load(new SecretBootstrapRequest(
                "orders", "prod",
                List.of("atlas-richie/prod/orders/common", "atlas-richie/prod/orders/components"),
                List.of()));

        assertThat(result.providerId()).isEqualTo("aliyun");
        assertThat(result.version()).hasSize(64);
        assertThat(result.values())
                .containsEntry("platform.oauth.client-secret", "one")
                .containsEntry("platform.component.storage.token", "two");
        assertThat(gateway.lastSecretRequest.getSecretName())
                .isEqualTo("company/atlas-richie/prod/orders/components");
    }

    @Test
    void failsClosedWhenBootstrapBundlesConflict() {
        gateway.put("company/one", response("v1", "{\"shared.key\":\"first\"}"));
        gateway.put("company/two", response("v2", "{\"shared.key\":\"second\"}"));

        assertThatThrownBy(() -> client.load(new SecretBootstrapRequest(
                "orders", "prod", List.of("one", "two"), List.of())))
                .isInstanceOf(SecretBootstrapException.class)
                .hasMessageContaining("conflict");
    }

    @Test
    void resolvesLogicalSecretAndVersionStageInsideProvider() {
        gateway.put("prod/orders/database", response(
                "db-v4", "{\"username\":\"orders\",\"password\":\"s3cret\"}"));
        SecretReference reference = new SecretReference(
                "database-password", SecretVersionSelector.stage("ACSPrevious"), null);

        try (var value = client.read(reference)) {
            assertThat(value.copyChars()).containsExactly("s3cret".toCharArray());
        }
        assertThat(gateway.lastSecretRequest.getSecretName()).isEqualTo("prod/orders/database");
        assertThat(gateway.lastSecretRequest.getVersionStage()).isEqualTo("ACSPrevious");
    }

    @Test
    void wrapsAndUnwrapsWhileKeepingKeyIdentityAndContextInsideProvider() {
        byte[] dataKey = "01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8);
        gateway.unwrapped = dataKey;
        CryptoContext context = new CryptoContext("orders:42".getBytes(StandardCharsets.UTF_8), Map.of());

        WrappedKey wrapped = client.wrap(
                KeyReference.envelopeEncryption("default-envelope"), dataKey, context);
        byte[] unwrapped = client.unwrap(
                KeyReference.envelopeEncryption("default-envelope"), wrapped, context);

        assertThat(wrapped.algorithm()).isEqualTo("aliyun-kms-symmetric-default");
        assertThat(unwrapped).isEqualTo(dataKey);
        assertThat(gateway.lastEncryptRequest.getKeyId()).isEqualTo("alias/orders");
        assertThat(gateway.lastEncryptRequest.getEncryptionContext())
                .isEqualTo(gateway.lastDecryptRequest.getEncryptionContext());
        assertThat(gateway.lastEncryptRequest.getEncryptionContext().get("atlas-component"))
                .isEqualTo("secret-envelope");
        assertThat(gateway.lastEncryptRequest.getEncryptionContext())
                .containsKey("atlas-aad-sha256");
        assertThat(gateway.lastEncryptRequest.getEncryptionContext().get("atlas-key"))
                .isEqualTo("default-envelope");
        assertThat(gateway.lastEncryptRequest.getEncryptionContext().get("atlas-version"))
                .isEqualTo("current");
        assertThat(gateway.lastEncryptRequest.getEncryptionContext().get("atlas-purpose"))
                .isEqualTo("ENVELOPE_ENCRYPTION");
    }

    @Test
    void refusesUnmappedLogicalKey() {
        assertThatThrownBy(() -> client.wrap(
                KeyReference.envelopeEncryption("unknown-key"), new byte[32], CryptoContext.empty()))
                .isInstanceOf(SecretConfigurationException.class)
                .hasMessageContaining("logical key unknown-key");
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

    @Test
    void mapsOnlyDocumentedResourceNotFoundCodeToMissing() {
        gateway.failure = tea("Forbidden.ResourceNotFound");

        assertThatThrownBy(() -> client.read(SecretReference.latest("database-password")))
                .isInstanceOf(SecretException.class)
                .hasMessageContaining("missing");

        gateway.failure = tea("Forbidden.DKMSInstanceNotFound");
        assertThatThrownBy(() -> client.read(SecretReference.latest("database-password")))
                .isInstanceOf(SecretException.class)
                .hasMessageContaining("read failed");
    }

    private TeaException tea(String code) {
        TeaException exception = new TeaException();
        exception.setCode(code);
        return exception;
    }

    private GetSecretValueResponse response(String version, String value) {
        var stages = new GetSecretValueResponseBody.GetSecretValueResponseBodyVersionStages()
                .setVersionStage(List.of("ACSCurrent"));
        return new GetSecretValueResponse().setBody(new GetSecretValueResponseBody()
                .setSecretName("test")
                .setSecretData(value)
                .setSecretDataType("text")
                .setVersionId(version)
                .setVersionStages(stages)
                .setCreateTime("2026-08-22T00:00:00Z")
                .setRequestId("request-1"));
    }

    private static final class FakeGateway implements AliyunKmsGateway {
        private final Map<String, GetSecretValueResponse> values = new LinkedHashMap<>();
        private GetSecretValueRequest lastSecretRequest;
        private EncryptRequest lastEncryptRequest;
        private DecryptRequest lastDecryptRequest;
        private byte[] unwrapped;
        private TeaException failure;

        void put(String name, GetSecretValueResponse response) { values.put(name, response); }

        @Override public GetSecretValueResponse getSecretValue(GetSecretValueRequest request) {
            lastSecretRequest = request;
            if (failure != null) throw failure;
            return values.get(request.getSecretName());
        }
        @Override public EncryptResponse encrypt(EncryptRequest request) {
            lastEncryptRequest = request;
            return new EncryptResponse().setBody(new EncryptResponseBody()
                    .setKeyId(request.getKeyId())
                    .setCiphertextBlob("aliyun-kms-ciphertext"));
        }
        @Override public DecryptResponse decrypt(DecryptRequest request) {
            lastDecryptRequest = request;
            return new DecryptResponse().setBody(new DecryptResponseBody()
                    .setKeyId("alias/orders")
                    .setPlaintext(Base64.getEncoder().encodeToString(unwrapped)));
        }
        @Override public void close() { }
    }
}
