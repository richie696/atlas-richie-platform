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
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.security.Security;

/**
 * SM4 国密分组密码 Provider（GM/T 0002-2012）
 * <p>
 * 基于 Bouncy Castle 实现：
 * <ul>
 *   <li>默认模式：SM4/GCM/NoPadding（AEAD 模式，内置完整性校验）</li>
 *   <li>IV：12 字节随机，自动前置到密文</li>
 *   <li>认证标签：16 字节（128-bit）</li>
 *   <li>密钥长度：128-bit（SM4 标准固定长度）</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class SM4Provider implements CryptoProvider {

    private static final String CIPHER_TRANSFORM = "SM4/GCM/NoPadding";
    private static final String BC_PROVIDER = "BC";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int DEFAULT_KEY_SIZE = 128;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Override
    public byte[] encrypt(byte[] data, java.security.Key key) throws Exception {
        byte[] iv = generateIv(GCM_IV_LENGTH);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM, BC_PROVIDER);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

        byte[] ciphertext = cipher.doFinal(data);

        // 前置 IV: [IV(12) | ciphertext]
        byte[] result = new byte[GCM_IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, result, GCM_IV_LENGTH, ciphertext.length);
        return result;
    }

    @Override
    public byte[] decrypt(byte[] data, java.security.Key key) throws Exception {
        // 提取 IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] ciphertext = new byte[data.length - GCM_IV_LENGTH];
        System.arraycopy(data, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(data, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM, BC_PROVIDER);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        return cipher.doFinal(ciphertext);
    }

    @Override
    public SecretKey generateSymmetricKey(int keySize) throws Exception {
        // SM4 标准密钥长度固定为 128-bit
        KeyGenerator kg = KeyGenerator.getInstance("SM4", BC_PROVIDER);
        kg.init(DEFAULT_KEY_SIZE);
        return kg.generateKey();
    }

    private static byte[] generateIv(int length) {
        byte[] iv = new byte[length];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
