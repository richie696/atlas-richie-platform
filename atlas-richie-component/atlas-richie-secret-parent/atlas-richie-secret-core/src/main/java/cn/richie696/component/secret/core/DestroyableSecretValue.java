/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core;

import cn.richie696.component.secret.api.SecretValue;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 基于可覆盖 byte 数组的默认 SecretValue。
 */
public final class DestroyableSecretValue implements SecretValue {
    private byte[] value;
    private boolean destroyed;

    private DestroyableSecretValue(byte[] value) {
        this.value = value.clone();
    }

    public static DestroyableSecretValue ofBytes(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("secret value must not be null");
        }
        return new DestroyableSecretValue(value);
    }

    public static DestroyableSecretValue ofChars(char[] value) {
        if (value == null) {
            throw new IllegalArgumentException("secret value must not be null");
        }
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        try {
            return new DestroyableSecretValue(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public synchronized byte[] copyBytes() {
        ensureAvailable();
        return value.clone();
    }

    @Override
    public synchronized char[] copyChars() {
        ensureAvailable();
        char[] buffer = new char[value.length];
        CharBuffer target = CharBuffer.wrap(buffer);
        try {
            CoderResult result = StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(value), target, true);
            if (result.isError()) {
                result.throwException();
            }
            return Arrays.copyOf(buffer, target.position());
        } catch (CharacterCodingException exception) {
            throw new IllegalStateException("secret value is not valid UTF-8", exception);
        } finally {
            Arrays.fill(buffer, '\0');
        }
    }

    @Override
    public synchronized int size() {
        return destroyed ? 0 : value.length;
    }

    @Override
    public synchronized boolean destroyed() {
        return destroyed;
    }

    @Override
    public synchronized void close() {
        if (!destroyed) {
            Arrays.fill(value, (byte) 0);
            value = new byte[0];
            destroyed = true;
        }
    }

    @Override
    public String toString() {
        return "SecretValue[PROTECTED]";
    }

    private void ensureAvailable() {
        if (destroyed) {
            throw new IllegalStateException("secret value has been destroyed");
        }
    }
}
