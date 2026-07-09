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

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.security.spec.ECGenParameterSpec;

/**
 * SM2 国密公钥密码 Provider（GM/T 0003-2012）
 * <p>
 * 基于 Bouncy Castle JCA Provider 实现，支持三种能力：
 * <ul>
 *   <li><b>加密/解密</b>：SM2（C1C3C2 模式）</li>
 *   <li><b>数字签名</b>：SM3withSM2</li>
 *   <li><b>密钥协商</b>：ECDH 派生共享密钥</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class SM2Provider implements CryptoProvider {

    private static final String CURVE = "sm2p256v1";
    private static final String BC_PROVIDER = "BC";
    private static final String KEY_ALGORITHM = "EC";
    private static final String CIPHER_ALGORITHM = "SM2";
    private static final String SIGNATURE_ALGORITHM = "SM3withSM2";
    private static final String KEY_AGREEMENT = "ECDH";
    private static final String SYMMETRIC_ALGORITHM = "AES";

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public byte[] encrypt(byte[] data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BC_PROVIDER);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    @Override
    public byte[] decrypt(byte[] data, Key key) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM, BC_PROVIDER);
        cipher.init(Cipher.DECRYPT_MODE, key);
        return cipher.doFinal(data);
    }

    @Override
    public byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
        sig.initSign(privateKey);
        sig.update(data);
        return sig.sign();
    }

    @Override
    public boolean verify(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        Signature sig = Signature.getInstance(SIGNATURE_ALGORITHM, BC_PROVIDER);
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    @Override
    public KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM, BC_PROVIDER);
        kpg.initialize(new ECGenParameterSpec(CURVE), new SecureRandom());
        return kpg.generateKeyPair();
    }

    @Override
    public SecretKey generateSharedSecret(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance(KEY_AGREEMENT, BC_PROVIDER);
        ka.init(privateKey);
        ka.doPhase(publicKey, true);
        byte[] sharedSecret = ka.generateSecret();
        return new SecretKeySpec(sharedSecret, SYMMETRIC_ALGORITHM);
    }
}
