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
package cn.richie696.context.utils.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwsSignatureUtilsTest {

    private static final byte[] SIGNING_INPUT = "eyJhbGciOiJFUzI1NiJ9.eyJjb25maWciOiJpbW11dGFibGUifQ"
            .getBytes(StandardCharsets.US_ASCII);

    @Test
    void es256_signsAndVerifiesRfc7515SignatureFormat() {
        KeyPair keyPair = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);

        byte[] jwsSignature = JwsSignatureUtils.sign(Algorithm.ECDSA, SIGNING_INPUT, keyPair.getPrivate());

        assertEquals("ES256", JwsSignatureUtils.jwsAlgorithm(Algorithm.ECDSA));
        assertEquals(64, jwsSignature.length);
        assertTrue(JwsSignatureUtils.verify(Algorithm.ECDSA, SIGNING_INPUT, keyPair.getPublic(), jwsSignature));
    }

    @Test
    void es256_roundTripsDerAndJoseEncoding() {
        KeyPair keyPair = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);
        byte[] derSignature = CryptoUtils.sign(Algorithm.ECDSA, SIGNING_INPUT, keyPair.getPrivate());

        byte[] joseSignature = JwsSignatureUtils.derToJoseEs256(derSignature);

        assertEquals(64, joseSignature.length);
        assertTrue(CryptoUtils.verify(
                Algorithm.ECDSA,
                SIGNING_INPUT,
                keyPair.getPublic(),
                JwsSignatureUtils.joseEs256ToDer(joseSignature)));
        assertArrayEquals(joseSignature, JwsSignatureUtils.derToJoseEs256(JwsSignatureUtils.joseEs256ToDer(joseSignature)));
    }

    @Test
    void jwsVerificationFailsClosedForTamperedOrMalformedSignature() {
        KeyPair keyPair = CryptoUtils.generateKeyPair(Algorithm.ECDSA, 0);
        byte[] jwsSignature = JwsSignatureUtils.sign(Algorithm.ECDSA, SIGNING_INPUT, keyPair.getPrivate());

        assertFalse(JwsSignatureUtils.verify(
                Algorithm.ECDSA,
                "changed".getBytes(StandardCharsets.US_ASCII),
                keyPair.getPublic(),
                jwsSignature));
        assertFalse(JwsSignatureUtils.verify(Algorithm.ECDSA, SIGNING_INPUT, keyPair.getPublic(), new byte[63]));
    }

    @Test
    void eddsaUsesNativeJwsSignatureFormat() {
        KeyPair keyPair = CryptoUtils.generateKeyPair(Algorithm.EDDSA, 0);

        byte[] jwsSignature = JwsSignatureUtils.sign(Algorithm.EDDSA, SIGNING_INPUT, keyPair.getPrivate());

        assertEquals("EdDSA", JwsSignatureUtils.jwsAlgorithm(Algorithm.EDDSA));
        assertEquals(64, jwsSignature.length);
        assertTrue(JwsSignatureUtils.verify(Algorithm.EDDSA, SIGNING_INPUT, keyPair.getPublic(), jwsSignature));
    }
}
