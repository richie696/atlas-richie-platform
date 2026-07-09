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
package com.richie.context.utils.security;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * 加密算法 SPI 接口
 * <p>
 * 每个算法对应一个包级私有的 Provider 实现类，
 * 仅 {@link CryptoUtils} 通过 {@link Algorithm} 枚举调度，
 * 项目组无需直接依赖此接口。
 * </p>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
interface CryptoProvider {

    /**
     * 加密数据（适用：对称加密 / 非对称加密）
     *
     * @param data 明文数据
     * @param key  密钥（对称: SecretKey，非对称: PublicKey）
     * @return 密文数据
     */
    default byte[] encrypt(byte[] data, java.security.Key key) throws Exception {
        throw new UnsupportedOperationException("encrypt not supported by " + getClass().getSimpleName());
    }

    /**
     * 解密数据（适用：对称加密 / 非对称加密）
     *
     * @param data 密文数据
     * @param key  密钥（对称: SecretKey，非对称: PrivateKey）
     * @return 明文数据
     */
    default byte[] decrypt(byte[] data, java.security.Key key) throws Exception {
        throw new UnsupportedOperationException("decrypt not supported by " + getClass().getSimpleName());
    }

    /**
     * 数字签名
     *
     * @param data       待签名数据
     * @param privateKey 私钥
     * @return 签名结果
     */
    default byte[] sign(byte[] data, PrivateKey privateKey) throws Exception {
        throw new UnsupportedOperationException("sign not supported by " + getClass().getSimpleName());
    }

    /**
     * 验签
     *
     * @param data      原始数据
     * @param publicKey 公钥
     * @param signature 签名值
     * @return true=验证通过，false=验证失败
     */
    default boolean verify(byte[] data, PublicKey publicKey, byte[] signature) throws Exception {
        throw new UnsupportedOperationException("verify not supported by " + getClass().getSimpleName());
    }

    /**
     * 生成对称密钥
     *
     * @param keySize 密钥位数（如 AES: 128/256, SM4: 128）
     * @return 对称密钥
     */
    default SecretKey generateSymmetricKey(int keySize) throws Exception {
        throw new UnsupportedOperationException("generateSymmetricKey not supported by " + getClass().getSimpleName());
    }

    /**
     * 生成非对称密钥对
     *
     * @param keySize 密钥位数（如 RSA: 2048/3072/4096, DSA: 4096）
     * @return 密钥对
     */
    default KeyPair generateKeyPair(int keySize) throws Exception {
        throw new UnsupportedOperationException("generateKeyPair not supported by " + getClass().getSimpleName());
    }

    /**
     * 密钥协商，派生共享密钥
     *
     * @param privateKey 本地私钥
     * @param publicKey  对方公钥
     * @return 共享密钥
     */
    default SecretKey generateSharedSecret(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        throw new UnsupportedOperationException("generateSharedSecret not supported by " + getClass().getSimpleName());
    }
}
