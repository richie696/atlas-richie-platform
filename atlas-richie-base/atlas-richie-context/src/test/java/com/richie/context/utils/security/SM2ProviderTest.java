package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class SM2ProviderTest {

    private final SM2Provider provider = new SM2Provider();

    @Test
    void generateKeyPair() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("EC", kp.getPublic().getAlgorithm());
    }

    @Test
    void encryptDecrypt_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] plaintext = "Hello SM2!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, kp.getPublic());
        byte[] decrypted = provider.decrypt(ciphertext, kp.getPrivate());

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void decrypt_wrongKey_fails() throws Exception {
        KeyPair kp1 = provider.generateKeyPair(0);
        KeyPair kp2 = provider.generateKeyPair(0);
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, kp1.getPublic());
        assertThrows(Exception.class, () -> provider.decrypt(ciphertext, kp2.getPrivate()));
    }

    @Test
    void signVerify_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "SM3withSM2 sign test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }

    @Test
    void verify_tamperedData_fails() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "original".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());

        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        assertFalse(provider.verify(tampered, kp.getPublic(), signature));
    }

    @Test
    void verify_wrongKey_fails() throws Exception {
        KeyPair kp1 = provider.generateKeyPair(0);
        KeyPair kp2 = provider.generateKeyPair(0);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp1.getPrivate());
        assertFalse(provider.verify(data, kp2.getPublic(), signature));
    }

    @Test
    void generateSharedSecret_roundtrip() throws Exception {
        KeyPair alice = provider.generateKeyPair(0);
        KeyPair bob = provider.generateKeyPair(0);

        SecretKey aliceSecret = provider.generateSharedSecret(alice.getPrivate(), bob.getPublic());
        SecretKey bobSecret = provider.generateSharedSecret(bob.getPrivate(), alice.getPublic());

        assertArrayEquals(aliceSecret.getEncoded(), bobSecret.getEncoded(),
                "SM2 密钥协商双方应得到相同共享密钥");
    }

    @Test
    void encryptDecrypt_largeData() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] plaintext = new byte[1024]; // 1KB
        for (int i = 0; i < plaintext.length; i++) {
            plaintext[i] = (byte) (i % 256);
        }

        byte[] ciphertext = provider.encrypt(plaintext, kp.getPublic());
        byte[] decrypted = provider.decrypt(ciphertext, kp.getPrivate());

        assertArrayEquals(plaintext, decrypted);
    }
}
