package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.*;

class ECDSAProviderTest {

    private final ECDSAProvider provider = new ECDSAProvider();

    @Test
    void generateKeyPair() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        assertNotNull(kp.getPublic());
        assertNotNull(kp.getPrivate());
        assertEquals("EC", kp.getPublic().getAlgorithm());
    }

    @Test
    void signVerify_roundtrip() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "ECDSA test data".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }

    @Test
    void signVerify_deterministicSignature() throws Exception {
        // ECDSA 签名每次不同（随机 k），但验签应始终通过
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "deterministic? no, but verify always works".getBytes(StandardCharsets.UTF_8);

        byte[] sig1 = provider.sign(data, kp.getPrivate());
        byte[] sig2 = provider.sign(data, kp.getPrivate());

        assertTrue(provider.verify(data, kp.getPublic(), sig1));
        assertTrue(provider.verify(data, kp.getPublic(), sig2));
    }

    @Test
    void verify_tamperedData_fails() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = "original".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp.getPrivate());

        byte[] tampered = "modified".getBytes(StandardCharsets.UTF_8);
        boolean valid = provider.verify(tampered, kp.getPublic(), signature);

        assertFalse(valid);
    }

    @Test
    void verify_wrongKey_fails() throws Exception {
        KeyPair kp1 = provider.generateKeyPair(0);
        KeyPair kp2 = provider.generateKeyPair(0);
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);

        byte[] signature = provider.sign(data, kp1.getPrivate());
        boolean valid = provider.verify(data, kp2.getPublic(), signature);

        assertFalse(valid);
    }

    @Test
    void signVerify_largeData() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = new byte[1024 * 10]; // 10KB
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }

    @Test
    void signVerify_emptyData() throws Exception {
        KeyPair kp = provider.generateKeyPair(0);
        byte[] data = new byte[0];

        byte[] signature = provider.sign(data, kp.getPrivate());
        boolean valid = provider.verify(data, kp.getPublic(), signature);

        assertTrue(valid);
    }
}
