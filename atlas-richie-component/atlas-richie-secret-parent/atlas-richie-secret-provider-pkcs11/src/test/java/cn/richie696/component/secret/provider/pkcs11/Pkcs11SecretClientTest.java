package cn.richie696.component.secret.provider.pkcs11;

import cn.richie696.component.secret.api.SecretCapability;
import cn.richie696.component.secret.api.crypto.CryptoContext;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretCryptoException;
import cn.richie696.component.secret.bootstrap.BootstrapSecretProperties;
import cn.richie696.component.secret.bootstrap.SecretBootstrapState;
import cn.richie696.component.secret.bootstrap.spi.SecretBootstrapRequest;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Constructor;
import java.security.KeyStore;
import java.security.Provider;
import java.security.Security;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Pkcs11SecretClientTest {
    @Test
    void wrapsAndUnwrapsWithTheJdkKeyStoreBoundary() throws Exception {
        char[] pin = "pin".toCharArray();
        KeyStore keyStore = KeyStore.getInstance("JCEKS");
        keyStore.load(null, null);
        keyStore.setKeyEntry("physical", new SecretKeySpec(
                "0123456789abcdef".getBytes(), "AES"), pin, null);
        Pkcs11SecretProperties properties = new Pkcs11SecretProperties();
        properties.setPin(pin);
        properties.setKeyBindings(Map.of("logical", "physical"));
        BootstrapSecretProperties bootstrap = new BootstrapSecretProperties();
        Provider provider = Security.getProvider("SunJCE");
        Constructor<Pkcs11SecretClient> constructor = Pkcs11SecretClient.class.getDeclaredConstructor(
                String.class, String.class, Pkcs11SecretProperties.class, KeyStore.class,
                Provider.class, String.class, char[].class);
        constructor.setAccessible(true);
        try (var client = constructor.newInstance("pkcs11", "hash", properties, keyStore,
                provider, null, pin.clone())) {
            assertThat(client.descriptor().capabilities()).containsExactlyInAnyOrder(
                    SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP);
            byte[] plaintext = "0123456789abcdef".getBytes();
            WrappedKey wrapped = client.wrap(KeyReference.envelopeEncryption("logical"), plaintext, CryptoContext.empty());
            assertThat(wrapped.algorithm()).isEqualTo("pkcs11-aes-wrap");
            assertThat(client.unwrap(KeyReference.envelopeEncryption("logical"), wrapped, CryptoContext.empty()))
                    .containsExactly(plaintext);
            assertThat(client.load(new SecretBootstrapRequest("", "", java.util.List.of(), java.util.List.of()))
                    .version()).isEqualTo("kms-only");
            assertThatThrownBy(() -> client.sign(new KeyReference("logical", "current", KeyPurpose.SIGNING),
                    new byte[]{1}, CryptoContext.empty())).isInstanceOf(SecretCryptoException.class);
            assertThatThrownBy(() -> client.unwrap(KeyReference.envelopeEncryption("logical"),
                    new WrappedKey(new byte[]{1}, "bad"), CryptoContext.empty()))
                    .isInstanceOf(SecretCryptoException.class);
            var factory = new Pkcs11SecretBootstrapProviderFactory();
            assertThat(factory.providerType()).isEqualTo("pkcs11");
            assertThat(factory.capabilities()).containsExactlyInAnyOrder(
                    SecretCapability.KEY_WRAP, SecretCapability.KEY_UNWRAP,
                    SecretCapability.SIGN, SecretCapability.VERIFY);
            var state = new SecretBootstrapState(bootstrap, factory, client,
                    client.load(new SecretBootstrapRequest("", "", java.util.List.of(), java.util.List.of())));
            assertThat(new Pkcs11SecretAutoConfiguration().pkcs11SecretClient(state)).isSameAs(client);
        }
    }

    @Test
    void validatesPropertiesAndHashInputs() throws Exception {
        java.nio.file.Path library = java.nio.file.Files.createTempFile("pkcs11", ".so");
        Pkcs11SecretProperties properties = new Pkcs11SecretProperties();
        properties.setLibrary(library.toString());
        properties.setPin("pin".toCharArray());
        properties.setSlot(0);
        properties.setKeyBindings(Map.of("logical", "physical"));
        properties.setVerificationKeyBindings(Map.of("logical", java.util.List.of("physical")));
        Pkcs11SecretConfiguration.validate(properties);
        assertThat(Pkcs11SecretConfiguration.hash("pkcs11", properties)).hasSize(64);
        properties.setTokenLabel("unsupported");
        assertThatThrownBy(() -> Pkcs11SecretConfiguration.validate(properties))
                .hasMessageContaining("token-label");
        java.nio.file.Files.deleteIfExists(library);
    }
}
