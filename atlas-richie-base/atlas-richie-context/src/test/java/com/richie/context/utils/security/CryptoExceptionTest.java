package com.richie.context.utils.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CryptoExceptionTest {

    @Test
    void message() {
        CryptoException e = new CryptoException("test error", new RuntimeException("cause"));
        assertEquals("test error", e.getMessage());
    }

    @Test
    void cause() {
        RuntimeException cause = new RuntimeException("root cause");
        CryptoException e = new CryptoException("wrapped", cause);
        assertSame(cause, e.getCause());
    }

    @Test
    void isRuntimeException() {
        CryptoException e = new CryptoException("msg", new Exception());
        assertInstanceOf(RuntimeException.class, e);
    }

    @Test
    void nullMessage() {
        CryptoException e = new CryptoException(null, new Exception());
        assertNull(e.getMessage());
    }

    @Test
    void nullCause() {
        CryptoException e = new CryptoException("msg", null);
        assertNull(e.getCause());
    }
}
