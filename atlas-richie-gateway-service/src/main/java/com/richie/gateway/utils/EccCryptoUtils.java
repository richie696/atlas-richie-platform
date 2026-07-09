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
package com.richie.gateway.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * ECC加密解密工具类
 * 支持客户端与网关之间的请求参数加密解密
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-27
 */
@Slf4j
public class EccCryptoUtils {

    private static final String ECC_CURVE = "secp256r1";
    private static final String KEY_AGREEMENT_ALGORITHM = "ECDH";
    private static final String SYMMETRIC_ALGORITHM = "AES";
    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    /**
     * 生成ECC密钥对
     *
     * @return 密钥对
     * @throws Exception 加密异常
     */
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
        ECGenParameterSpec ecSpec = new ECGenParameterSpec(ECC_CURVE);
        keyGen.initialize(ecSpec);
        return keyGen.generateKeyPair();
    }

    /**
     * 将公钥转换为Base64字符串
     *
     * @param publicKey 公钥
     * @return Base64编码的公钥字符串
     */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * 将私钥转换为Base64字符串
     *
     * @param privateKey 私钥
     * @return Base64编码的私钥字符串
     */
    public static String privateKeyToBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /**
     * 从Base64字符串恢复公钥
     *
     * @param base64PublicKey Base64编码的公钥字符串
     * @return 公钥对象
     * @throws Exception 加密异常
     */
    public static PublicKey base64ToPublicKey(String base64PublicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PublicKey);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * 从Base64字符串恢复私钥
     *
     * @param base64PrivateKey Base64编码的私钥字符串
     * @return 私钥对象
     * @throws Exception 加密异常
     */
    public static PrivateKey base64ToPrivateKey(String base64PrivateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64PrivateKey);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return keyFactory.generatePrivate(keySpec);
    }

    /**
     * 生成共享密钥
     *
     * @param privateKey 本地私钥
     * @param publicKey  远程公钥
     * @return 共享密钥
     * @throws Exception 加密异常
     */
    public static SecretKey generateSharedSecret(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(publicKey, true);
        byte[] sharedSecret = keyAgreement.generateSecret();
        return new SecretKeySpec(sharedSecret, SYMMETRIC_ALGORITHM);
    }

    /**
     * 加密数据
     *
     * @param data        待加密数据
     * @param sharedKey   共享密钥
     * @return 加密后的数据（Base64编码）
     * @throws Exception 加密异常
     */
    public static String encrypt(String data, SecretKey sharedKey) throws Exception {
        if (!StringUtils.hasText(data)) {
            return data;
        }

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        byte[] iv = generateIV();
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.ENCRYPT_MODE, sharedKey, gcmSpec);

        byte[] encryptedData = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    /**
     * 解密数据
     *
     * @param encryptedData 加密数据（Base64编码）
     * @param sharedKey     共享密钥
     * @return 解密后的数据
     * @throws Exception 解密异常
     */
    public static String decrypt(String encryptedData, SecretKey sharedKey) throws Exception {
        if (!StringUtils.hasText(encryptedData)) {
            return encryptedData;
        }

        byte[] combined = Base64.getDecoder().decode(encryptedData);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byte[] encryptedBytes = new byte[combined.length - GCM_IV_LENGTH];

        System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);
        System.arraycopy(combined, GCM_IV_LENGTH, encryptedBytes, 0, encryptedBytes.length);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, sharedKey, gcmSpec);

        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }

    /**
     * 生成随机IV
     *
     * @return IV字节数组
     */
    private static byte[] generateIV() {
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        return iv;
    }

    /**
     * 验证密钥对是否匹配
     *
     * @param publicKey  公钥
     * @param privateKey 私钥
     * @return 是否匹配
     */
    public static boolean validateKeyPair(PublicKey publicKey, PrivateKey privateKey) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
            ECGenParameterSpec ecSpec = new ECGenParameterSpec(ECC_CURVE);
            keyGen.initialize(ecSpec);
            
            // 生成测试密钥对
            KeyPair testKeyPair = keyGen.generateKeyPair();
            
            // 使用公钥和测试私钥生成共享密钥1
            SecretKey sharedKey1 = generateSharedSecret(testKeyPair.getPrivate(), publicKey);
            
            // 使用私钥和测试公钥生成共享密钥2
            SecretKey sharedKey2 = generateSharedSecret(privateKey, testKeyPair.getPublic());
            
            // 比较两个共享密钥是否相同
            return java.util.Arrays.equals(sharedKey1.getEncoded(), sharedKey2.getEncoded());
        } catch (Exception e) {
            log.error("验证密钥对失败", e);
            return false;
        }
    }
} 
