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

import static org.junit.jupiter.api.Assertions.*;

class AlgorithmTest {

    @Test
    void values_allDefined() {
        Algorithm[] values = Algorithm.values();
        assertEquals(7, values.length);
    }

    @Test
    void valueOf_aes() {
        assertEquals(Algorithm.AES, Algorithm.valueOf("AES"));
    }

    @Test
    void valueOf_rsa() {
        assertEquals(Algorithm.RSA, Algorithm.valueOf("RSA"));
    }

    @Test
    void valueOf_ecdsa() {
        assertEquals(Algorithm.ECDSA, Algorithm.valueOf("ECDSA"));
    }

    @Test
    void valueOf_ecdh() {
        assertEquals(Algorithm.ECDH, Algorithm.valueOf("ECDH"));
    }

    @Test
    void valueOf_dsa() {
        assertEquals(Algorithm.DSA, Algorithm.valueOf("DSA"));
    }

    @Test
    void valueOf_sm2() {
        assertEquals(Algorithm.SM2, Algorithm.valueOf("SM2"));
    }

    @Test
    void valueOf_sm4() {
        assertEquals(Algorithm.SM4, Algorithm.valueOf("SM4"));
    }
}
