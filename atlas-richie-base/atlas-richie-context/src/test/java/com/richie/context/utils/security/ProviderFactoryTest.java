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

class ProviderFactoryTest {

    @Test
    void provider_aes() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.AES);
        assertNotNull(p);
        assertInstanceOf(AESProvider.class, p);
    }

    @Test
    void provider_rsa() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.RSA);
        assertNotNull(p);
        assertInstanceOf(RSAProvider.class, p);
    }

    @Test
    void provider_ecdsa() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.ECDSA);
        assertInstanceOf(ECDSAProvider.class, p);
    }

    @Test
    void provider_ecdh() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.ECDH);
        assertInstanceOf(ECDHProvider.class, p);
    }

    @Test
    void provider_dsa() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.DSA);
        assertInstanceOf(DSAProvider.class, p);
    }

    @Test
    void provider_sm2() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.SM2);
        assertInstanceOf(SM2Provider.class, p);
    }

    @Test
    void provider_sm4() {
        CryptoProvider p = ProviderFactory.provider(Algorithm.SM4);
        assertInstanceOf(SM4Provider.class, p);
    }

    @Test
    void provider_sameInstance() {
        CryptoProvider p1 = ProviderFactory.provider(Algorithm.AES);
        CryptoProvider p2 = ProviderFactory.provider(Algorithm.AES);
        assertSame(p1, p2, "同一种算法应返回相同单例");
    }

    @Test
    void provider_unknownAlgorithm() {
        assertThrows(IllegalArgumentException.class,
                () -> ProviderFactory.provider(null));
    }
}
