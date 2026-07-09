/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import static org.junit.jupiter.api.Assertions.*;

class DSAProviderTest {

    private final DSAProvider provider = new DSAProvider();

    @Test
    void generateKeyPair_default() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("DSA", kp.getPublic().getAlgorithm());
    }

    @Test
    void signVerify_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "DSA test data".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }

    @Test
    void signVerify_multipleTimes() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "repeated sign".getBytes(StandardCharsets.UTF_8);

        for (int i = 0; i < 3; i++) {
            byte[] signature = provider.sign(data, kp.getPrivate());
            assertTrue(provider.verify(data, kp.getPublic(), signature));
        }
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
    void signVerify_largeData() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = new byte[1024 * 50]; // 50KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 128);
        }

        byte[] signature = provider.sign(data, kp.getPrivate());
        assertTrue(provider.verify(data, kp.getPublic(), signature));
    }
}
