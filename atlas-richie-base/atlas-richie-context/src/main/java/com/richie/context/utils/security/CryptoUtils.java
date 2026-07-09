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
 * 加密工具类 — 统一外观入口
 * <p>
 * 项目组只需记住这一个类名，通过 {@link Algorithm} 枚举选择算法即可。
 * 所有 Provider 实现为包级私有，对外完全隐藏。
 * </p>
 *
 * <pre>{@code
 * // 对称加密（AES/SM4）
 * byte[] ciphertext = CryptoUtils.encrypt(Algorithm.AES, plaintext, secretKey);
 * byte[] plaintext  = CryptoUtils.decrypt(Algorithm.AES, ciphertext, secretKey);
 *
 * // 非对称加密（RSA/SM2）
 * byte[] encrypted  = CryptoUtils.encrypt(Algorithm.RSA, data, publicKey);
 * byte[] decrypted  = CryptoUtils.decrypt(Algorithm.RSA, encrypted, privateKey);
 *
 * // 数字签名（ECDSA/DSA/RSA/SM2）
 * byte[] sig = CryptoUtils.sign(Algorithm.ECDSA, data, privateKey);
 * boolean ok = CryptoUtils.verify(Algorithm.ECDSA, data, publicKey, sig);
 *
 * // 密钥生成
 * SecretKey aesKey = CryptoUtils.generateKey(Algorithm.AES, 256);
 * KeyPair rsaPair  = CryptoUtils.generateKeyPair(Algorithm.RSA, 2048);
 *
 * // 密钥协商（ECDH/SM2）
 * SecretKey shared = CryptoUtils.generateSharedSecret(Algorithm.ECDH, privateKey, publicKey);
 * }</pre>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
public final class CryptoUtils {

    private CryptoUtils() {
    }

    // ========== 加密 ==========

    /**
     * 加密数据
     *
     * @param algorithm 算法
     * @param data      明文
     * @param key       密钥（对称: SecretKey，非对称: PublicKey）
     * @return 密文
     */
    public static byte[] encrypt(Algorithm algorithm, byte[] data, java.security.Key key) {
        return silently(() -> ProviderFactory.provider(algorithm).encrypt(data, key));
    }

    /**
     * 解密数据
     *
     * @param algorithm 算法
     * @param data      密文
     * @param key       密钥（对称: SecretKey，非对称: PrivateKey）
     * @return 明文
     */
    public static byte[] decrypt(Algorithm algorithm, byte[] data, java.security.Key key) {
        return silently(() -> ProviderFactory.provider(algorithm).decrypt(data, key));
    }

    // ========== 数字签名 ==========

    /**
     * 数字签名
     *
     * @param algorithm  算法
     * @param data       待签名数据
     * @param privateKey 私钥
     * @return 签名值
     */
    public static byte[] sign(Algorithm algorithm, byte[] data, PrivateKey privateKey) {
        return silently(() -> ProviderFactory.provider(algorithm).sign(data, privateKey));
    }

    /**
     * 验签
     *
     * @param algorithm 算法
     * @param data      原始数据
     * @param publicKey 公钥
     * @param signature 签名值
     * @return true=通过，false=不通过
     */
    public static boolean verify(Algorithm algorithm, byte[] data, PublicKey publicKey, byte[] signature) {
        try {
            return ProviderFactory.provider(algorithm).verify(data, publicKey, signature);
        } catch (Exception e) {
            throw new CryptoException("verify failed", e);
        }
    }

    // ========== 密钥生成 ==========

    /**
     * 生成对称密钥
     *
     * @param algorithm 算法（AES / SM4）
     * @param keySize   密钥位数（AES: 128/256, SM4: 128，传 0 使用默认值）
     * @return 对称密钥
     */
    public static SecretKey generateKey(Algorithm algorithm, int keySize) {
        return silently(() -> ProviderFactory.provider(algorithm).generateSymmetricKey(keySize));
    }

    /**
     * 生成非对称密钥对
     *
     * @param algorithm 算法（RSA / ECDSA / ECDH / DSA / SM2）
     * @param keySize   密钥位数（RSA: 2048, DSA: 4096, EC 系传 0 使用默认曲线）
     * @return 密钥对
     */
    public static KeyPair generateKeyPair(Algorithm algorithm, int keySize) {
        return silently(() -> ProviderFactory.provider(algorithm).generateKeyPair(keySize));
    }

    // ========== 密钥协商 ==========

    /**
     * 密钥协商，派生共享密钥
     *
     * @param algorithm  算法（ECDH / SM2）
     * @param privateKey 本地私钥
     * @param publicKey  对方公钥
     * @return 共享密钥
     */
    public static SecretKey generateSharedSecret(Algorithm algorithm, PrivateKey privateKey, PublicKey publicKey) {
        return silently(() -> ProviderFactory.provider(algorithm).generateSharedSecret(privateKey, publicKey));
    }

    // ========== 内部 ==========

    private static <T> T silently(CryptoSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException(e.getMessage(), e);
        }
    }

    @FunctionalInterface
    private interface CryptoSupplier<T> {
        T get() throws Exception;
    }
}
