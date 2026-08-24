/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.core.crypto;

import cn.richie696.component.secret.api.crypto.CipherEnvelope;
import cn.richie696.component.secret.api.crypto.KeyPurpose;
import cn.richie696.component.secret.api.crypto.KeyReference;
import cn.richie696.component.secret.api.crypto.WrappedKey;
import cn.richie696.component.secret.api.exception.SecretIntegrityException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 严格长度限制的 arse:v1 二进制信封编解码器。
 */
public final class ArseEnvelopeCodec {
    public static final String TEXT_PREFIX = "arse:v1:";
    private static final byte[] MAGIC = {'A', 'R', 'S', 'E'};
    private static final int MAX_TEXT = 4096;
    private static final int MAX_WRAPPED_KEY = 64 * 1024;
    private static final int MAX_NONCE = 32;
    private static final int MAX_CIPHERTEXT = 16 * 1024 * 1024;

    public byte[] encode(CipherEnvelope envelope) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(buffer)) {
                output.write(MAGIC);
                output.writeByte(envelope.version());
                writeString(output, envelope.algorithm());
                KeyReference key = envelope.keyReference();
                writeString(output, key.logicalKey());
                writeString(output, key.version());
                writeString(output, key.purpose().name());
                writeString(output, envelope.wrappedKey().algorithm());
                writeBytes(output, envelope.wrappedKey().value(), MAX_WRAPPED_KEY);
                writeBytes(output, envelope.nonce(), MAX_NONCE);
                writeBytes(output, envelope.ciphertext(), MAX_CIPHERTEXT);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode CipherEnvelope", exception);
        }
    }

    public String encodeToString(CipherEnvelope envelope) {
        return TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(encode(envelope));
    }

    public CipherEnvelope decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0 || encoded.length > MAX_CIPHERTEXT + MAX_WRAPPED_KEY + 32768) {
            throw new SecretIntegrityException("CipherEnvelope size is invalid");
        }
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            byte[] magic = input.readNBytes(MAGIC.length);
            if (!java.util.Arrays.equals(MAGIC, magic)) {
                throw new SecretIntegrityException("CipherEnvelope magic is invalid");
            }
            int version = input.readUnsignedByte();
            if (version != DefaultEnvelopeCrypto.LEGACY_ENVELOPE_VERSION
                    && version != DefaultEnvelopeCrypto.ENVELOPE_VERSION) {
                throw new SecretIntegrityException("CipherEnvelope version is unsupported");
            }
            String algorithm = readString(input);
            KeyReference keyReference = new KeyReference(
                    readString(input),
                    readString(input),
                    KeyPurpose.valueOf(readString(input)));
            String wrappedKeyAlgorithm = readString(input);
            WrappedKey wrappedKey = new WrappedKey(
                    readBytes(input, MAX_WRAPPED_KEY),
                    wrappedKeyAlgorithm);
            byte[] nonce = readBytes(input, MAX_NONCE);
            byte[] ciphertext = readBytes(input, MAX_CIPHERTEXT);
            if (input.available() != 0) {
                throw new SecretIntegrityException("CipherEnvelope contains trailing data");
            }
            return new CipherEnvelope(version, algorithm, keyReference, wrappedKey, nonce, ciphertext);
        } catch (SecretIntegrityException exception) {
            throw exception;
        } catch (EOFException exception) {
            throw new SecretIntegrityException("CipherEnvelope is truncated", exception);
        } catch (IOException | IllegalArgumentException exception) {
            throw new SecretIntegrityException("CipherEnvelope is malformed", exception);
        }
    }

    public CipherEnvelope decode(String encoded) {
        if (encoded == null || !encoded.startsWith(TEXT_PREFIX)) {
            throw new SecretIntegrityException("CipherEnvelope text prefix is invalid");
        }
        if (encoded.length() > (MAX_CIPHERTEXT + MAX_WRAPPED_KEY + 32768) * 4L / 3L + TEXT_PREFIX.length() + 4) {
            throw new SecretIntegrityException("CipherEnvelope text size is invalid");
        }
        try {
            return decode(Base64.getUrlDecoder().decode(encoded.substring(TEXT_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            throw new SecretIntegrityException("CipherEnvelope Base64 is invalid", exception);
        }
    }

    private void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > MAX_TEXT) {
            throw new SecretIntegrityException("CipherEnvelope text field length is invalid");
        }
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private String readString(DataInputStream input) throws IOException {
        byte[] encoded = readBytes(input, MAX_TEXT);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new SecretIntegrityException("CipherEnvelope text field is not valid UTF-8", exception);
        }
    }

    private void writeBytes(DataOutputStream output, byte[] value, int maximum) throws IOException {
        if (value.length == 0 || value.length > maximum) {
            throw new SecretIntegrityException("CipherEnvelope binary field length is invalid");
        }
        output.writeInt(value.length);
        output.write(value);
    }

    private byte[] readBytes(DataInputStream input, int maximum) throws IOException {
        int length = input.readInt();
        if (length <= 0 || length > maximum) {
            throw new SecretIntegrityException("CipherEnvelope field length is invalid");
        }
        byte[] value = input.readNBytes(length);
        if (value.length != length) {
            throw new EOFException("CipherEnvelope field is truncated");
        }
        return value;
    }
}
