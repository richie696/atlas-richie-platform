package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.mfa.core.config.properties.MfaLocalCryptoProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
}
