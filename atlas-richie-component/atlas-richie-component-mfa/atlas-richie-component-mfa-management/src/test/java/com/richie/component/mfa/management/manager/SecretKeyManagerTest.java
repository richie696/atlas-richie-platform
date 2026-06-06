package com.richie.component.mfa.management.manager;

import com.richie.component.cache.GlobalCache;
import com.richie.component.mfa.core.constant.MfaStatusEnum;
import com.richie.component.mfa.core.crypto.KeyManagementProvider;
import com.richie.component.mfa.core.entity.MfaUserInfo;
import com.richie.component.mfa.core.util.MfaKeyUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecretKeyManagerTest {

    @Mock
    private KeyManagementProvider kmsProvider;

    @InjectMocks
    private SecretKeyManager secretKeyManager;

    @Test
    void generateSecretKey_returnsBase32WithoutPadding() {
        String secret = secretKeyManager.generateSecretKey();
        assertThat(secret).isNotBlank().doesNotContain("=");
    }

    @Test
    void encryptSecretKey_fallsBackWhenKmsUnavailable() {
        when(kmsProvider.isAvailable()).thenReturn(false);
        assertThat(secretKeyManager.encryptSecretKey("plain")).isEqualTo("plain");
    }

    @Test
    void encryptSecretKey_usesKmsWhenAvailable() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.encrypt("plain")).thenReturn("cipher");

        assertThat(secretKeyManager.encryptSecretKey("plain")).isEqualTo("cipher");
    }

    @Test
    void decryptSecretKey_fallsBackWhenKmsUnavailable() {
        when(kmsProvider.isAvailable()).thenReturn(false);
        assertThat(secretKeyManager.decryptSecretKey("plain")).isEqualTo("plain");
    }

    @Test
    void encryptSecretKey_fallsBackWhenKmsThrows() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.encrypt("plain")).thenThrow(new RuntimeException("kms down"));

        assertThat(secretKeyManager.encryptSecretKey("plain")).isEqualTo("plain");
    }

    @Test
    void decryptSecretKey_throwsWhenKmsDecryptFails() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.decrypt("cipher")).thenThrow(new RuntimeException("bad cipher"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretKeyManager.decryptSecretKey("cipher"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("KMS解密失败");
    }

    @Test
    void storeAndRetrieveSecret_delegatesToKms() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.storeSecret(null, "u1", "secret")).thenReturn("mfa/u1");
        when(kmsProvider.retrieveSecret("mfa/u1")).thenReturn("secret");

        assertThat(secretKeyManager.storeSecret(null, "u1", "secret")).isEqualTo("mfa/u1");
        assertThat(secretKeyManager.retrieveSecret("mfa/u1")).isEqualTo("secret");
        assertThat(secretKeyManager.retrieveSecret(null, "u1")).isEqualTo("secret");
    }

    @Test
    void deleteSecret_delegatesToKms() {
        secretKeyManager.deleteSecret("t1", "u1");

        verify(kmsProvider).deleteSecret("mfa/t1/u1");
    }

    @Test
    void storeSecret_throwsWhenKmsUnavailable() {
        when(kmsProvider.isAvailable()).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretKeyManager.storeSecret(null, "u1", "s"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("KMS提供方不可用");
    }

    @Test
    void storeSecret_throwsWhenKmsFails() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.storeSecret(null, "u1", "s")).thenThrow(new RuntimeException("store failed"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretKeyManager.storeSecret(null, "u1", "s"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("KMS存储密钥失败");
    }

    @Test
    void retrieveSecret_throwsWhenKmsFails() {
        when(kmsProvider.isAvailable()).thenReturn(true);
        when(kmsProvider.retrieveSecret("mfa/u1")).thenThrow(new RuntimeException("missing"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretKeyManager.retrieveSecret("mfa/u1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("KMS检索密钥失败");
    }

    @Test
    void retrieveSecret_throwsWhenKmsUnavailable() {
        when(kmsProvider.isAvailable()).thenReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> secretKeyManager.retrieveSecret("mfa/u1"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("KMS提供方不可用");
    }
}
