package cn.richie696.component.secret.provider.kmip;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapContext;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmipSecretClientTest {
    @Test
    void createsKmsClientAndExposesOnlyWrappingCapabilities() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.kmip.endpoint", "kmips://localhost:5696")
                .withProperty("platform.component.secret.kmip.key-bindings.orders", "physical-orders-key");
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        try (var client = (KmipSecretClient) new KmipSecretBootstrapProviderFactory().create(
                bootstrap, new SecretBootstrapContext(environment, getClass().getClassLoader(),
                        "orders", "kmip", "platform.component.secret.kmip"))) {
            assertThat(client.descriptor().providerType()).isEqualTo("kmip");
            assertThat(client.descriptor().capabilities()).containsExactlyInAnyOrder(
                    SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
            assertThat(client.secretBackend()).isEmpty();
            assertThat(client.keyWrappingBackend()).contains(client);
            assertThat(client.configurationHash()).hasSize(64);
            assertThat(client.load(new SecretBootstrapRequest("orders", "prod", java.util.List.of(), java.util.List.of()))
                    .version()).isEqualTo("kms-only");
            SecretBootstrapState state = new SecretBootstrapState(
                    bootstrap, new KmipSecretBootstrapProviderFactory(), client,
                    client.load(new SecretBootstrapRequest("orders", "prod", java.util.List.of(), java.util.List.of())));
            assertThat(new KmipSecretAutoConfiguration().kmipSecretClient(state)).isSameAs(client);
        }
    }

    @Test
    void validatesCryptoInputsAndClosedLifecycleBeforeOpeningNetwork() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("platform.component.secret.kmip.endpoint", "kmips://localhost:5696");
        try (var client = (KmipSecretClient) new KmipSecretBootstrapProviderFactory().create(
                new BootstrapSecretProperties(), new SecretBootstrapContext(environment, getClass().getClassLoader(),
                        "kmip", "kmip", "platform.component.secret.kmip"))) {
            assertThatThrownBy(() -> client.wrap(KeyReference.envelopeEncryption("key"), new byte[0], CryptoContext.empty()))
                    .isInstanceOf(SecretCryptoException.class);
            assertThatThrownBy(() -> client.unwrap(KeyReference.envelopeEncryption("key"),
                    new WrappedKey(new byte[]{1}, "unsupported"), CryptoContext.empty()))
                    .isInstanceOf(SecretCryptoException.class);
            client.close();
            assertThatThrownBy(() -> client.load(new SecretBootstrapRequest("", "", java.util.List.of(), java.util.List.of())))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("closed");
        }
    }

    @Test
    void parsesSuccessfulAndInvalidKmipResponses() {
        byte[] response = KmipTtlv.structure(0x42007b,
                KmipTtlv.structure(0x42000f,
                        KmipTtlv.enumeration(0x42005c, 31),
                        KmipTtlv.enumeration(0x42007f, 0),
                        KmipTtlv.structure(0x42007c,
                                KmipTtlv.bytes(0x4200c2, new byte[]{1, 2, 3}))));
        assertThat(KmipSecretClient.parseResponse(31, response)).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> KmipSecretClient.parseResponse(32, response))
                .isInstanceOf(SecretCryptoException.class)
                .hasMessageContaining("does not match");
        assertThatThrownBy(() -> KmipSecretClient.parseResponse(31,
                KmipTtlv.structure(0x42007b)))
                .isInstanceOf(SecretCryptoException.class)
                .hasMessageContaining("Batch Item");
    }

    @Test
    void validatesConfigurationAndRetriesTransientIo() throws Exception {
        KmipSecretProperties properties = new KmipSecretProperties();
        properties.setEndpoint(java.net.URI.create("kmips://localhost:5696"));
        properties.setKeyBindings(Map.of("logical", "physical"));
        KmipSecretConfiguration.validate(properties);
        assertThat(KmipSecretConfiguration.hash("kmip", properties)).hasSize(64);
        properties.setEndpoint(java.net.URI.create("http://localhost:5696"));
        assertThatThrownBy(() -> KmipSecretConfiguration.validate(properties))
                .isInstanceOf(SecretConfigurationException.class);

        int[] attempts = {0};
        assertThat(KmipSecretClient.retryIo(2, () -> {
            if (++attempts[0] == 1) {
                throw new java.io.IOException("transient");
            }
            return "ok";
        })).isEqualTo("ok");
        assertThat(attempts[0]).isEqualTo(2);
    }

    @Test
    void executesEncryptAndDecryptAgainstTlsSessionBoundary() throws Exception {
        KmipSecretProperties properties = new KmipSecretProperties();
        properties.setEndpoint(java.net.URI.create("kmips://localhost:5696"));
        properties.setKeyBindings(Map.of("logical", "physical"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        bootstrap.getResilience().setMaxAttempts(1);
        SSLSocketFactory factory = mock(SSLSocketFactory.class);
        SSLSocket encryptSocket = socket(responseFor(31, new byte[]{9, 8, 7}));
        SSLSocket decryptSocket = socket(responseFor(32, new byte[]{1, 2, 3}));
        when(factory.createSocket()).thenReturn(encryptSocket, decryptSocket);
        Constructor<KmipSecretClient> constructor = KmipSecretClient.class.getDeclaredConstructor(
                String.class, String.class, KmipSecretProperties.class,
                BootstrapSecretProperties.class, SSLSocketFactory.class);
        constructor.setAccessible(true);
        try (var client = constructor.newInstance("kmip", "hash", properties, bootstrap, factory)) {
            var wrapped = client.wrap(KeyReference.envelopeEncryption("logical"), new byte[]{4, 5}, CryptoContext.empty());
            assertThat(wrapped.algorithm()).isEqualTo("kmip-aes-kwp");
            assertThat(client.unwrap(KeyReference.envelopeEncryption("logical"), wrapped, CryptoContext.empty()))
                    .containsExactly(1, 2, 3);
        }
    }

    private static SSLSocket socket(byte[] response) throws Exception {
        SSLSocket socket = mock(SSLSocket.class);
        when(socket.getInputStream()).thenReturn(new ByteArrayInputStream(response));
        when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        doNothing().when(socket).connect(org.mockito.ArgumentMatchers.any(InetSocketAddress.class), org.mockito.ArgumentMatchers.anyInt());
        doNothing().when(socket).setSoTimeout(org.mockito.ArgumentMatchers.anyInt());
        doNothing().when(socket).startHandshake();
        return socket;
    }

    private static byte[] responseFor(int operation, byte[] data) {
        return KmipTtlv.structure(0x42007b,
                KmipTtlv.structure(0x42000f,
                        KmipTtlv.enumeration(0x42005c, operation),
                        KmipTtlv.enumeration(0x42007f, 0),
                        KmipTtlv.structure(0x42007c, KmipTtlv.bytes(0x4200c2, data))));
    }
}
