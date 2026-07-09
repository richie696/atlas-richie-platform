/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.context.utils.security;

import java.security.*;

/**
 * DSA 数字签名 Provider
 * <p>
 * 符合 FIPS Pub 186-4 标准：
 * <ul>
 *   <li>推荐密钥长度：2048-bit（FIPS 186-4 指定 DSA 模数长度为 2048/3072）</li>
 *   <li>签名算法：SHA256withDSA</li>
 * </ul>
 *
 * @author richie696
 * @version 1.1
 * @since 2026/06/02
 */
class DSAProvider implements CryptoProvider {

    private static final String KEY_ALGORITHM = "DSA";
    private static final String SIGNATURE_ALGORITHM = "SHA256withDSA";
    private static final int DEFAULT_KEY_SIZE = 2048;

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    @Override
    public boolean verify(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    @Override
    public KeyPair generateKeyPair(int keySize) throws Exception {
        int size = keySize <= 0 ? DEFAULT_KEY_SIZE : keySize;
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        kpg.initialize(size);
        return kpg.generateKeyPair();
    }
}
