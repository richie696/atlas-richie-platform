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

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.junit.jupiter.api.Assertions.*;

class RSAProviderTest {

    private final RSAProvider provider = new RSAProvider();

    @Test
    void generateKeyPair_default() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("RSA", kp.getPublic().getAlgorithm());
        // 默认 2048-bit
        assertEquals(2048, ((java.security.interfaces.RSAKey) kp.getPublic()).getModulus().bitLength());
    }

    @Test
    void generateKeyPair_2048() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        assertEquals(2048, ((java.security.interfaces.RSAKey) kp.getPublic()).getModulus().bitLength());
    }

    @Test
    void encryptDecrypt_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] plaintext = "Hello RSA OAEP!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, kp.getPublic());
        byte[] decrypted = provider.decrypt(ciphertext, kp.getPrivate());

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_singleBlock() throws Exception {
        // OAEPWithSHA-256: 最大明文 = 2048/8 - 66 = 190 字节
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] plaintext = new byte[190];
        for (int i = 0; i < plaintext.length; i++) {
            plaintext[i] = (byte) i;
        }

        byte[] ciphertext = provider.encrypt(plaintext, kp.getPublic());
        byte[] decrypted = provider.decrypt(ciphertext, kp.getPrivate());

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encryptDecrypt_multiBlock() throws Exception {
        // 超过单块限制 → 触发分块加密
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] plaintext = new byte[256]; // > 190
        for (int i = 0; i < plaintext.length; i++) {
            plaintext[i] = (byte) (i % 128);
        }

        byte[] ciphertext = provider.encrypt(plaintext, kp.getPublic());
        byte[] decrypted = provider.decrypt(ciphertext, kp.getPrivate());

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_requiresPublicKey() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> provider.encrypt(data, kp.getPrivate()));
    }

    @Test
    void decrypt_requiresPrivateKey() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,
                () -> provider.decrypt(data, kp.getPublic()));
    }

    @Test
    void decrypt_wrongKey_fails() throws Exception {
        KeyPair kp1 = provider.generateKeyPair(2048);
        KeyPair kp2 = provider.generateKeyPair(2048);
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = provider.encrypt(plaintext, kp1.getPublic());
        assertThrows(Exception.class, () -> provider.decrypt(ciphertext, kp2.getPrivate()));
    }

    @Test
    void signVerify_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] data = "data to sign".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }

    @Test
    void verify_tamperedData_fails() throws Exception {
        KeyPair kp = provider.generateKeyPair(2048);
        byte[] data = "original".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());

        byte[] tampered = "tampered".getBytes(StandardCharsets.UTF_8);
        boolean valid = provider.verify(tampered, kp.getPublic(), signature);

        assertFalse(valid);
    }

    @Test
    void verify_wrongKey_fails() throws Exception {
        KeyPair kp1 = provider.generateKeyPair(2048);
        KeyPair kp2 = provider.generateKeyPair(2048);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp1.getPrivate());
        boolean valid = provider.verify(data, kp2.getPublic(), signature);

        assertFalse(valid);
    }
}
