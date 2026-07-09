/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class ECDHProviderTest {

    private final ECDHProvider provider = new ECDHProvider();

    @Test
    void generateKeyPair() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("EC", kp.getPublic().getAlgorithm());
    }

    @Test
    void sharedSecret_aliceAndBob() throws Exception {
        KeyPair alice = provider.generateKeyPair(0);
        KeyPair bob = provider.generateKeyPair(0);

        SecretKey aliceSecret = provider.generateSharedSecret(alice.getPrivate(), bob.getPublic());
        SecretKey bobSecret = provider.generateSharedSecret(bob.getPrivate(), alice.getPublic());

        assertNotNull(aliceSecret);
        assertNotNull(bobSecret);
        assertArrayEquals(aliceSecret.getEncoded(), bobSecret.getEncoded(),
                "Alice 和 Bob 应派生出相同的共享密钥");
    }

    @Test
    void sharedSecret_samePartyDifferentKeys() throws Exception {
        KeyPair alice1 = provider.generateKeyPair(0);
        KeyPair alice2 = provider.generateKeyPair(0);
        KeyPair bob = provider.generateKeyPair(0);

        SecretKey secretA = provider.generateSharedSecret(alice1.getPrivate(), bob.getPublic());
        SecretKey secretB = provider.generateSharedSecret(alice2.getPrivate(), bob.getPublic());

        // 不同的本地密钥对，与同一对方协商得到不同的共享密钥
        assertFalse(java.util.Arrays.equals(secretA.getEncoded(), secretB.getEncoded()));
    }

    @Test
    void sharedSecret_algorithmIsAES() throws Exception {
        KeyPair alice = provider.generateKeyPair(0);
        KeyPair bob = provider.generateKeyPair(0);

        SecretKey secret = provider.generateSharedSecret(alice.getPrivate(), bob.getPublic());

        assertEquals("AES", secret.getAlgorithm());
    }

    @Test
    void sharedSecret_nonNull() throws Exception {
        KeyPair alice = provider.generateKeyPair(0);
        KeyPair bob = provider.generateKeyPair(0);

        SecretKey secret = provider.generateSharedSecret(alice.getPrivate(), bob.getPublic());

        assertNotNull(secret.getEncoded());
        assertTrue(secret.getEncoded().length > 0);
    }
}
