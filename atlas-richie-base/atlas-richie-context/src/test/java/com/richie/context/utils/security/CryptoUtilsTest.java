/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilsTest {

    private static final byte[] DATA = "CryptoUtils integration test data".getBytes(StandardCharsets.UTF_8);

    // ========== AES 对称加密 ==========

    @Test
    void aes_encryptDecrypt() {
        SecretKey key = CryptoUtils.generateKey(Algorithm.AES, 256);
        byte[] ciphertext = CryptoUtils.encrypt(Algorithm.AES, DATA, key);
        byte[] decrypted = CryptoUtils.decrypt(Algorithm.AES, ciphertext, key);
        assertArrayEquals(DATA, decrypted);
    }

    @Test
    void aes_wrongKey_fails() {
        SecretKey key1 = CryptoUtils.generateKey(Algorithm.AES, 256);
        SecretKey key2 = CryptoUtils.generateKey(Algorithm.AES, 128);
        byte[] ciphertext = CryptoUtils.encrypt(Algorithm.AES, DATA, key1);
        assertThrows(CryptoException.class, () -> CryptoUtils.decrypt(Algorithm.AES, ciphertext, key2));
    }

    // ========== RSA 非对称加密 ==========

    @Test
    void rsa_encryptDecrypt() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.RSA, 2048);
        byte[] ciphertext = CryptoUtils.encrypt(Algorithm.RSA, DATA, kp.getPublic());
        byte[] decrypted = CryptoUtils.decrypt(Algorithm.RSA, ciphertext, kp.getPrivate());
        assertArrayEquals(DATA, decrypted);
    }

    @Test
    void rsa_signVerify() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.RSA, 2048);
        byte[] signature = CryptoUtils.sign(Algorithm.RSA, DATA, kp.getPrivate());
        assertTrue(CryptoUtils.verify(Algorithm.RSA, DATA, kp.getPublic(), signature));
    }

    @Test
    void rsa_verifyTampered_fails() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.RSA, 2048);
        byte[] signature = CryptoUtils.sign(Algorithm.RSA, DATA, kp.getPrivate());
        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        assertFalse(CryptoUtils.verify(Algorithm.RSA, tampered, kp.getPublic(), signature));
    }

    // ========== ECDSA 签名 ==========

    @Test
    void ecdsa_signVerify() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);
        byte[] signature = CryptoUtils.sign(Algorithm.ECDSA, DATA, kp.getPrivate());
        assertTrue(CryptoUtils.verify(Algorithm.ECDSA, DATA, kp.getPublic(), signature));
    }

    @Test
    void ecdsa_wrongKey_fails() {
        KeyPair kp1 = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);
        KeyPair kp2 = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);
        byte[] signature = CryptoUtils.sign(Algorithm.ECDSA, DATA, kp1.getPrivate());
        assertFalse(CryptoUtils.verify(Algorithm.ECDSA, DATA, kp2.getPublic(), signature));
    }

    // ========== DSA 签名 ==========

    @Test
    void dsa_signVerify() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.DSA, 0);
        byte[] signature = CryptoUtils.sign(Algorithm.DSA, DATA, kp.getPrivate());
        assertTrue(CryptoUtils.verify(Algorithm.DSA, DATA, kp.getPublic(), signature));
    }

    // ========== ECDH 密钥协商 ==========

    @Test
    void ecdh_sharedSecret() {
        KeyPair alice = CryptoUtils.generateKeyPair(Algorithm.ECDH, 0);
        KeyPair bob = CryptoUtils.generateKeyPair(Algorithm.ECDH, 0);

        SecretKey aliceSecret = CryptoUtils.generateSharedSecret(Algorithm.ECDH, alice.getPrivate(), bob.getPublic());
        SecretKey bobSecret = CryptoUtils.generateSharedSecret(Algorithm.ECDH, bob.getPrivate(), alice.getPublic());

        assertArrayEquals(aliceSecret.getEncoded(), bobSecret.getEncoded());
    }

    // ========== SM2 ==========

    @Test
    void sm2_encryptDecrypt() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.SM2, 0);
        byte[] ciphertext = CryptoUtils.encrypt(Algorithm.SM2, DATA, kp.getPublic());
        byte[] decrypted = CryptoUtils.decrypt(Algorithm.SM2, ciphertext, kp.getPrivate());
        assertArrayEquals(DATA, decrypted);
    }

    @Test
    void sm2_signVerify() {
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.SM2, 0);
        byte[] signature = CryptoUtils.sign(Algorithm.SM2, DATA, kp.getPrivate());
        assertTrue(CryptoUtils.verify(Algorithm.SM2, DATA, kp.getPublic(), signature));
    }

    @Test
    void sm2_sharedSecret() {
        KeyPair alice = CryptoUtils.generateKeyPair(Algorithm.SM2, 0);
        KeyPair bob = CryptoUtils.generateKeyPair(Algorithm.SM2, 0);

        SecretKey aliceSecret = CryptoUtils.generateSharedSecret(Algorithm.SM2, alice.getPrivate(), bob.getPublic());
        SecretKey bobSecret = CryptoUtils.generateSharedSecret(Algorithm.SM2, bob.getPrivate(), alice.getPublic());

        assertArrayEquals(aliceSecret.getEncoded(), bobSecret.getEncoded());
    }

    // ========== SM4 对称加密 ==========

    @Test
    void sm4_encryptDecrypt() {
        SecretKey key = CryptoUtils.generateKey(Algorithm.SM4, 0);
        byte[] ciphertext = CryptoUtils.encrypt(Algorithm.SM4, DATA, key);
        byte[] decrypted = CryptoUtils.decrypt(Algorithm.SM4, ciphertext, key);
        assertArrayEquals(DATA, decrypted);
    }

    // ========== 异常包装 ==========

    @Test
    void encrypt_withNullKey_throwsCryptoException() {
        assertThrows(CryptoException.class,
                () -> CryptoUtils.encrypt(Algorithm.AES, DATA, null));
    }

    @Test
    void unsupportedOperation_throwsUnsupportedOperationException() {
        // AESProvider 不支持 sign，CryptoUtils.silently() 直接将 RuntimeException 原样抛出
        KeyPair kp = CryptoUtils.generateKeyPair(Algorithm.RSA, 2048);
        assertThrows(UnsupportedOperationException.class,
                () -> CryptoUtils.sign(Algorithm.AES, DATA, kp.getPrivate()));
    }
}
