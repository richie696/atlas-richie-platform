package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.mfa.core.config.properties.MfaLocalCryptoProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalKeyManagementEngineTest {

    private LocalKeyManagementEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LocalKeyManagementEngine(new MfaLocalCryptoProperties(), new MfaTenantSupport());
        engine.init();
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plaintext = "mfa-secret-value";
        String ciphertext = engine.encrypt(plaintext);

        assertThat(ciphertext).isNotBlank().isNotEqualTo(plaintext);
        assertThat(engine.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_blankInput_returnsBlank() {
        assertThat(engine.encrypt("")).isEmpty();
        assertThat(engine.encrypt(null)).isNull();
    }

    @Test
    void isAvailable_afterInit() {
        assertThat(engine.isAvailable()).isTrue();
    }

    @Test
    void parseSecretReference_invalidFormat() {
        assertThatThrownBy(() -> engine.retrieveSecret("invalid/path"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void decrypt_blankInput_returnsBlank() {
        assertThat(engine.decrypt("")).isEmpty();
        assertThat(engine.decrypt(null)).isNull();
    }

    @Test
    void init_rejectsInvalidKeyLength() {
        MfaLocalCryptoProperties props = new MfaLocalCryptoProperties();
        props.setSecretKey("dG9vX3Nob3J0"); // too short
        LocalKeyManagementEngine invalid = new LocalKeyManagementEngine(props, new MfaTenantSupport());

        assertThatThrownBy(invalid::init).isInstanceOf(RuntimeException.class);
    }

    @Test
    void decrypt_invalidCiphertext_throws() {
        assertThatThrownBy(() -> engine.decrypt("not-valid-base64-cipher!!!"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void isAvailable_returnsFalseBeforeInit() {
        LocalKeyManagementEngine uninitEngine = new LocalKeyManagementEngine(new MfaLocalCryptoProperties(), new MfaTenantSupport());
        assertThat(uninitEngine.isAvailable()).isFalse();
    }

    @Test
    void decrypt_shortCiphertext_throws() {
        // 4 bytes encoded in Base64 (less than 12-byte ivLength, triggers combined.length < ivLength)
        String shortCipher = Base64.getEncoder().encodeToString(new byte[4]);
        assertThatThrownBy(() -> engine.decrypt(shortCipher))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void encryptAndDecrypt_withDefaultAlgorithmParams() {
        MfaLocalCryptoProperties props = new MfaLocalCryptoProperties();
        props.setAlgorithm("");
        props.setGcmIvLength(0);
        props.setGcmTagLength(0);
        LocalKeyManagementEngine defaultEngine = new LocalKeyManagementEngine(props, new MfaTenantSupport());
        defaultEngine.init();

        String plaintext = "mfa-secret-value";
        String ciphertext = defaultEngine.encrypt(plaintext);
        assertThat(ciphertext).isNotBlank().isNotEqualTo(plaintext);
        assertThat(defaultEngine.decrypt(ciphertext)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_invalidAlgorithm_throws() {
        MfaLocalCryptoProperties props = new MfaLocalCryptoProperties();
        props.setAlgorithm("AES/INVALID/NoPadding");
        LocalKeyManagementEngine badEngine = new LocalKeyManagementEngine(props, new MfaTenantSupport());
        badEngine.init();

        assertThatThrownBy(() -> badEngine.encrypt("test"))
                .isInstanceOf(RuntimeException.class);
    }
}
