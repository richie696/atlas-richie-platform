package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SM4ProviderTest {

    private final SM4Provider provider = new SM4Provider();

    @Test
    void generateSymmetricKey() throws Exception {
        SecretKey key = provider.generateSymmetricKey(0);
        assertNotNull(key);
        assertEquals("SM4", key.getAlgorithm());
        assertEquals(16, key.getEncoded().length); // 128-bit = 16 bytes
    }

    @Test
    void encryptDecrypt_roundtrip() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        byte[] plaintext = "Hello SM4/GCM!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_emptyData() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        byte[] plaintext = new byte[0];

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_largeData() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        byte[] plaintext = new byte[1024 * 50]; // 50KB
        Arrays.fill(plaintext, (byte) 'B');

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_randomIv() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext1 = provider.encrypt(plaintext, key);
        byte[] ciphertext2 = provider.encrypt(plaintext, key);

        assertFalse(Arrays.equals(ciphertext1, ciphertext2),
                "相同明文、相同密钥，两次加密结果不同（随机 IV）");
    }

    @Test
    void decrypt_wrongKey_fails() throws Exception {
        SecretKey key1 = provider.generateSymmetricKey(128);
        SecretKey key2 = provider.generateSymmetricKey(128);
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key1);
        assertThrows(Exception.class, () -> provider.decrypt(ciphertext, key2));
    }

    @Test
    void decrypt_tamperedCiphertext_fails() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key);
        ciphertext[ciphertext.length - 1] ^= 0x01;
        byte[] finalCiphertext = ciphertext;
        assertThrows(Exception.class, () -> provider.decrypt(finalCiphertext, key));
    }
}
