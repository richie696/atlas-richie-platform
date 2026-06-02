package com.richie.context.utils.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;

/**
 * AES 对称加密 Provider
 * <p>
 * 符合 FIPS Pub 197 标准：
 * <ul>
 *   <li>默认模式：AES/GCM/NoPadding（AEAD 模式，内置完整性校验）</li>
 *   <li>IV：12 字节随机，自动前置到密文</li>
 *   <li>认证标签：16 字节（128-bit）</li>
 *   <li>密钥长度：128/256-bit</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class AESProvider implements CryptoProvider {

    private static final String CIPHER_TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int DEFAULT_KEY_SIZE = 256;

    @Override
    public byte[] encrypt(byte[] data, java.security.Key key) throws Exception {
        byte[] iv = generateIv(GCM_IV_LENGTH);

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
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

        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

        return cipher.doFinal(ciphertext);
    }

    @Override
    public SecretKey generateSymmetricKey(int keySize) throws Exception {
        int size = keySize <= 0 ? DEFAULT_KEY_SIZE : keySize;
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(size);
        return kg.generateKey();
    }

    private static byte[] generateIv(int length) {
        byte[] iv = new byte[length];
        new SecureRandom().nextBytes(iv);
        return iv;
    }
}
