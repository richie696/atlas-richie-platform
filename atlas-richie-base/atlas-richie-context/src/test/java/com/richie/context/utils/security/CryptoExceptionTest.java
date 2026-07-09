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
