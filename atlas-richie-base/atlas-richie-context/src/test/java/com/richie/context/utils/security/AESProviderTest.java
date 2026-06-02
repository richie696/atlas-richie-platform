package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class AESProviderTest {

    private final AESProvider provider = new AESProvider();

    @Test
    void generateSymmetricKey_default() throws Exception {
        SecretKey key = provider.generateSymmetricKey(0);
        assertNotNull(key);
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length); // 256-bit = 32 bytes
    }

    @Test
    void generateSymmetricKey_128() throws Exception {
        SecretKey key = provider.generateSymmetricKey(128);
        assertEquals(16, key.getEncoded().length);
    }

    @Test
    void generateSymmetricKey_256() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    void encryptDecrypt_roundtrip() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        byte[] plaintext = "Hello AES/GCM!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_emptyData() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        byte[] plaintext = new byte[0];

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_largeData() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        byte[] plaintext = new byte[1024 * 100]; // 100KB
        Arrays.fill(plaintext, (byte) 'A');

        byte[] ciphertext = provider.encrypt(plaintext, key);
        byte[] decrypted = provider.decrypt(ciphertext, key);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_randomIv() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        byte[] plaintext = "test".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext1 = provider.encrypt(plaintext, key);
        byte[] ciphertext2 = provider.encrypt(plaintext, key);

        // 相同明文、相同密钥，两次加密结果不同（随机 IV）
        assertFalse(Arrays.equals(ciphertext1, ciphertext2));
    }

    @Test
    void decrypt_wrongKey_fails() throws Exception {
        SecretKey key1 = provider.generateSymmetricKey(256);
        SecretKey key2 = provider.generateSymmetricKey(128);
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key1);
        // 用不同 key 解密应抛异常（GCM AEAD 认证标签不匹配）
        assertThrows(Exception.class, () -> provider.decrypt(ciphertext, key2));
    }

    @Test
    void decrypt_tamperedCiphertext_fails() throws Exception {
        SecretKey key = provider.generateSymmetricKey(256);
        byte[] plaintext = "data".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, key);
        // 篡改密文中的一个字节
        ciphertext[ciphertext.length - 1] ^= 0x01;
        byte[] finalCiphertext = ciphertext;
        assertThrows(Exception.class, () -> provider.decrypt(finalCiphertext, key));
    }
}
