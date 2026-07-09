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

import java.security.*;
import java.security.spec.ECGenParameterSpec;

/**
 * ECDSA 椭圆曲线数字签名 Provider
 * <p>
 * 符合 FIPS Pub 186-4 标准：
 * <ul>
 *   <li>曲线：secp256r1（P-256）</li>
 *   <li>签名算法：SHA256withECDSA</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class ECDSAProvider implements CryptoProvider {

    private static final String CURVE = "secp256r1";
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";
    private static final String KEY_ALGORITHM = "EC";

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
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        kpg.initialize(new ECGenParameterSpec(CURVE));
        return kpg.generateKeyPair();
    }
}
