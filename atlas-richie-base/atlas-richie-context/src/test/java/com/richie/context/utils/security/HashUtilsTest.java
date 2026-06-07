package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class HashUtilsTest {

    private static final String INPUT = "atlas-richie-platform";
    private static final byte[] INPUT_BYTES = INPUT.getBytes(StandardCharsets.UTF_8);

    @Test
    void md5_string() {
        String hash = HashUtils.md5(INPUT);
        assertNotNull(hash);
        assertEquals(32, hash.length());
        assertTrue(hash.matches("[0-9a-f]{32}"));
    }

    @Test
    void md5_bytes() {
        String hash = HashUtils.md5(INPUT_BYTES);
        assertNotNull(hash);
        assertEquals(32, hash.length());
    }

    @Test
    void md5_deterministic() {
        String h1 = HashUtils.md5(INPUT);
        String h2 = HashUtils.md5(INPUT);
        assertEquals(h1, h2);
    }

    @Test
    void md5_differentInput() {
        String h1 = HashUtils.md5("hello");
        String h2 = HashUtils.md5("world");
        assertNotEquals(h1, h2);
    }

    @Test
    void sha256_string() {
        String hash = HashUtils.sha256(INPUT);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void sha256_bytes() {
        String hash = HashUtils.sha256(INPUT_BYTES);
        assertEquals(64, hash.length());
    }

    @Test
    void sha256_deterministic() {
        String h1 = HashUtils.sha256(INPUT);
        String h2 = HashUtils.sha256(INPUT);
        assertEquals(h1, h2);
    }

    @Test
    void sha512_bytes() {
        byte[] hash = HashUtils.sha512(INPUT_BYTES);
        assertNotNull(hash);
        assertEquals(64, hash.length); // SHA-512 = 64 bytes
    }

    @Test
    void sha512_string() {
        byte[] hash = HashUtils.sha512(INPUT);
        assertEquals(64, hash.length);
    }

    @Test
    void sha512WithString_string() {
        String hash = HashUtils.sha512WithString(INPUT);
        assertEquals(128, hash.length());
        assertTrue(hash.matches("[0-9a-f]{128}"));
    }

    @Test
    void sha512WithBase64_string() {
        String hash = HashUtils.sha512WithBase64(INPUT);
        assertNotNull(hash);
        assertTrue(hash.length() > 0);
    }

    @Test
    void sm3_string() {
        String hash = HashUtils.sm3(INPUT);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void sm3_bytes() {
        String hash = HashUtils.sm3(INPUT_BYTES);
        assertEquals(64, hash.length());
    }

    @Test
    void sm3_deterministic() {
        String h1 = HashUtils.sm3(INPUT);
        String h2 = HashUtils.sm3(INPUT);
        assertEquals(h1, h2);
    }

    @Test
    void sm3_differentInput() {
        String h1 = HashUtils.sm3("abc");
        String h2 = HashUtils.sm3("abcd");
        assertNotEquals(h1, h2);
    }

    @Test
    void hashcode() {
        String hash = HashUtils.hashcode(INPUT, 42);
        assertNotNull(hash);
        assertTrue(hash.length() > 0);
    }

    @Test
    void hashcode_bytes() {
        String hash = HashUtils.hashcode(INPUT_BYTES, 42);
        assertNotNull(hash);
        assertTrue(hash.length() > 0);
    }

    @Test
    void hashcode_differentSeed() {
        String h1 = HashUtils.hashcode(INPUT, 1);
        String h2 = HashUtils.hashcode(INPUT, 2);
        assertNotEquals(h1, h2);
    }
}
