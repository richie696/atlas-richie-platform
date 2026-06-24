package com.richie.component.mfa.core.crypto.provider;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.mfa.core.config.properties.MfaLocalCryptoProperties;
import com.richie.component.mfa.core.support.MfaTenantSupport;
import com.richie.contract.gateway.config.TenantFilterConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

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

    @Test
    void storeSecret_storesCorrectKeyAndValue() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            ValueOps valueOps = mock(ValueOps.class);
            cache.when(GlobalCache::value).thenReturn(valueOps);

            String path = engine.storeSecret(null, "user-1", "my-secret");

            assertThat(path).isEqualTo("mfa/user-1");
            verify(valueOps).set(eq("mfa:secret:user-1"), eq("my-secret"), anyLong());
        }
    }

    @Test
    void storeSecret_withTenant_includesTenantInKey() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            ValueOps valueOps = mock(ValueOps.class);
            cache.when(GlobalCache::value).thenReturn(valueOps);

            MfaTenantSupport tenantSupport = new MfaTenantSupport();
            ReflectionTestUtils.setField(tenantSupport, "gatewayContract", null);
            TenantFilterConfig tenantFilterConfig = new TenantFilterConfig();
            tenantFilterConfig.setEnable(true);
            ReflectionTestUtils.setField(tenantSupport, "tenantFilterConfig", tenantFilterConfig);

            LocalKeyManagementEngine tenantEngine = new LocalKeyManagementEngine(new MfaLocalCryptoProperties(), tenantSupport);
            tenantEngine.init();

            String path = tenantEngine.storeSecret("tenant-x", "user-1", "secret");

            assertThat(path).isEqualTo("mfa/tenant-x/user-1");
            verify(valueOps).set(eq("mfa:secret:tenant-x:user-1"), eq("secret"), anyLong());
        }
    }

    @Test
    void retrieveSecret_returnsStoredSecret() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            ValueOps valueOps = mock(ValueOps.class);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.get(eq("mfa:secret:user-1"), eq(String.class))).thenReturn("stored-secret");

            String secret = engine.retrieveSecret("mfa/user-1");

            assertThat(secret).isEqualTo("stored-secret");
        }
    }

    @Test
    void retrieveSecret_throwsWhenNotFound() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            ValueOps valueOps = mock(ValueOps.class);
            cache.when(GlobalCache::value).thenReturn(valueOps);
            when(valueOps.get(anyString(), eq(String.class))).thenReturn(null);

            assertThatThrownBy(() -> engine.retrieveSecret("mfa/user-1"))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("密钥不存在");
        }
    }

    @Test
    void deleteSecret_removesCacheKey() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            KeyOps keyOps = mock(KeyOps.class);
            cache.when(GlobalCache::key).thenReturn(keyOps);

            engine.deleteSecret("mfa/user-1");

            verify(keyOps).removeCache(eq("mfa:secret:user-1"));
        }
    }

    @Test
    void deleteSecret_withTenant_removesCorrectKey() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            KeyOps keyOps = mock(KeyOps.class);
            cache.when(GlobalCache::key).thenReturn(keyOps);

            MfaTenantSupport tenantSupport = new MfaTenantSupport();
            ReflectionTestUtils.setField(tenantSupport, "gatewayContract", null);
            TenantFilterConfig tenantFilterConfig = new TenantFilterConfig();
            tenantFilterConfig.setEnable(true);
            ReflectionTestUtils.setField(tenantSupport, "tenantFilterConfig", tenantFilterConfig);

            LocalKeyManagementEngine tenantEngine = new LocalKeyManagementEngine(new MfaLocalCryptoProperties(), tenantSupport);
            tenantEngine.init();

            tenantEngine.deleteSecret("mfa/tenant-x/user-1");

            verify(keyOps).removeCache(eq("mfa:secret:tenant-x:user-1"));
        }
    }
}
