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

import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Arrays;

/**
 * JWS 签名编解码工具。
 *
 * <p>仅支持本中台推荐用于配置证明的 {@code ES256}（ECDSA P-256）和 {@code EdDSA}
 * （Ed25519）。调用方负责构造和 Base64URL 编码 {@code BASE64URL(header) + '.' +
 * BASE64URL(payload)}；本类只处理其字节签名，避免把自定义 token 误当成标准 JWS。</p>
 *
 * <p>Java 的 {@code SHA256withECDSA} 产生 ASN.1 DER 签名，而 RFC 7515 的 ES256 使用
 * 固定 64 字节 {@code R || S}。本类负责该转换；Ed25519 的原生签名已符合 JWS 格式。</p>
 */
public final class JwsSignatureUtils {

    private static final int ES256_PART_BYTES = 32;
    private static final int ES256_SIGNATURE_BYTES = ES256_PART_BYTES * 2;

    private JwsSignatureUtils() {
    }

    /** 返回 JWS {@code alg} 名称。 */
    public static String jwsAlgorithm(Algorithm algorithm) {
        return switch (algorithm) {
            case ECDSA -> "ES256";
            case EDDSA -> "EdDSA";
            default -> throw new IllegalArgumentException("JWS only supports ECDSA(ES256) or EDDSA(Ed25519), got " + algorithm);
        };
    }

    /** 对 JWS signing input 签名，返回 RFC 7515 规定的签名字节。 */
    public static byte[] sign(Algorithm algorithm, byte[] signingInput, PrivateKey privateKey) {
        requireJwsAlgorithm(algorithm);
        byte[] signature = CryptoUtils.sign(algorithm, signingInput, privateKey);
        return algorithm == Algorithm.ECDSA ? derToJoseEs256(signature) : signature;
    }

    /** 验证 RFC 7515 格式的 JWS 签名。格式非法或验签失败均返回 {@code false}。 */
    public static boolean verify(Algorithm algorithm, byte[] signingInput, PublicKey publicKey, byte[] jwsSignature) {
        try {
            requireJwsAlgorithm(algorithm);
            byte[] signature = algorithm == Algorithm.ECDSA ? joseEs256ToDer(jwsSignature) : jwsSignature;
            return CryptoUtils.verify(algorithm, signingInput, publicKey, signature);
        } catch (RuntimeException e) {
            return false;
        }
    }

    static byte[] derToJoseEs256(byte[] derSignature) {
        DerReader reader = new DerReader(derSignature);
        if (reader.readByte() != 0x30) {
            throw new IllegalArgumentException("ECDSA signature is not a DER sequence");
        }
        int sequenceLength = reader.readLength();
        if (sequenceLength != reader.remaining()) {
            throw new IllegalArgumentException("Invalid DER ECDSA sequence length");
        }
        byte[] r = reader.readPositiveInteger();
        byte[] s = reader.readPositiveInteger();
        if (reader.remaining() != 0) {
            throw new IllegalArgumentException("Unexpected data after DER ECDSA signature");
        }
        byte[] jose = new byte[ES256_SIGNATURE_BYTES];
        copyUnsignedInteger(r, jose, 0);
        copyUnsignedInteger(s, jose, ES256_PART_BYTES);
        return jose;
    }

    static byte[] joseEs256ToDer(byte[] joseSignature) {
        if (joseSignature == null || joseSignature.length != ES256_SIGNATURE_BYTES) {
            throw new IllegalArgumentException("ES256 JWS signature must be exactly 64 bytes");
        }
        byte[] r = derInteger(Arrays.copyOfRange(joseSignature, 0, ES256_PART_BYTES));
        byte[] s = derInteger(Arrays.copyOfRange(joseSignature, ES256_PART_BYTES, ES256_SIGNATURE_BYTES));
        int sequenceLength = r.length + s.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(sequenceLength + 2);
        out.write(0x30);
        out.write(sequenceLength);
        out.writeBytes(r);
        out.writeBytes(s);
        return out.toByteArray();
    }

    private static byte[] derInteger(byte[] fixedWidth) {
        int first = 0;
        while (first < fixedWidth.length - 1 && fixedWidth[first] == 0) {
            first++;
        }
        int valueLength = fixedWidth.length - first;
        boolean needsLeadingZero = (fixedWidth[first] & 0x80) != 0;
        byte[] result = new byte[2 + valueLength + (needsLeadingZero ? 1 : 0)];
        result[0] = 0x02;
        result[1] = (byte) (valueLength + (needsLeadingZero ? 1 : 0));
        System.arraycopy(fixedWidth, first, result, 2 + (needsLeadingZero ? 1 : 0), valueLength);
        return result;
    }

    private static void copyUnsignedInteger(byte[] value, byte[] target, int offset) {
        int first = 0;
        while (first < value.length - 1 && value[first] == 0) {
            first++;
        }
        int length = value.length - first;
        if (length > ES256_PART_BYTES) {
            throw new IllegalArgumentException("ECDSA integer does not fit ES256");
        }
        System.arraycopy(value, first, target, offset + ES256_PART_BYTES - length, length);
    }

    private static void requireJwsAlgorithm(Algorithm algorithm) {
        jwsAlgorithm(algorithm);
    }

    private static final class DerReader {
        private final byte[] input;
        private int position;

        private DerReader(byte[] input) {
            if (input == null || input.length == 0) {
                throw new IllegalArgumentException("DER signature is empty");
            }
            this.input = input;
        }

        private int readByte() {
            if (position >= input.length) {
                throw new IllegalArgumentException("Unexpected end of DER signature");
            }
            return input[position++] & 0xff;
        }

        private int readLength() {
            int first = readByte();
            if ((first & 0x80) == 0) {
                return first;
            }
            int count = first & 0x7f;
            if (count == 0 || count > 2 || count > remaining()) {
                throw new IllegalArgumentException("Invalid DER length");
            }
            int length = 0;
            for (int i = 0; i < count; i++) {
                length = (length << 8) | readByte();
            }
            if (length < 128) {
                throw new IllegalArgumentException("DER length must use shortest encoding");
            }
            return length;
        }

        private byte[] readPositiveInteger() {
            if (readByte() != 0x02) {
                throw new IllegalArgumentException("Expected DER INTEGER in ECDSA signature");
            }
            int length = readLength();
            if (length == 0 || length > remaining()) {
                throw new IllegalArgumentException("Invalid DER INTEGER length");
            }
            byte[] value = Arrays.copyOfRange(input, position, position + length);
            position += length;
            if ((value[0] & 0x80) != 0) {
                throw new IllegalArgumentException("DER ECDSA INTEGER must be non-negative");
            }
            if (value.length > 1 && value[0] == 0 && (value[1] & 0x80) == 0) {
                throw new IllegalArgumentException("DER ECDSA INTEGER has redundant leading zero");
            }
            return value;
        }

        private int remaining() {
            return input.length - position;
        }
    }
}
