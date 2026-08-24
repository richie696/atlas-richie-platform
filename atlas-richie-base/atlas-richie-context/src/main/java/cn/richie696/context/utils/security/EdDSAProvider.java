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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * EdDSA 数字签名 Provider。
 *
 * <p>JDK 原生 {@code Ed25519} 实现（RFC 8032），不引入额外 Provider。JWS 对应
 * {@code alg=EdDSA}；与 ECDSA 不同，JCA 输出已是 JOSE 可直接使用的固定长度签名，
 * 无需 DER 转换。</p>
 */
class EdDSAProvider implements CryptoProvider {

    static final String KEY_ALGORITHM = "Ed25519";
    private static final String SIGNATURE_ALGORITHM = "Ed25519";

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return signature.sign();
    }

    @Override
    public boolean verify(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature verifier = Signature.getInstance(SIGNATURE_ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(data);
        return verifier.verify(signature);
    }

    @Override
    public KeyPair generateKeyPair(int keySize) throws Exception {
        if (keySize != 0 && keySize != 255) {
            throw new IllegalArgumentException("Ed25519 does not accept a configurable key size; use 0 or 255");
        }
        return KeyPairGenerator.getInstance(KEY_ALGORITHM).generateKeyPair();
    }
}
