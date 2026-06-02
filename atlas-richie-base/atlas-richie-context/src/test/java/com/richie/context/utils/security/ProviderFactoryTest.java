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
