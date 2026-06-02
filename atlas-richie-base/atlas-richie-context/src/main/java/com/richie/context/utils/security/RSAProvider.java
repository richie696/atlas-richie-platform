package com.richie.context.utils.security;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.security.*;
import java.security.interfaces.RSAKey;

/**
 * RSA 加密/签名 Provider
 * <p>
 * 符合 PKCS#1 v2.2 标准：
 * <ul>
 *   <li>加密填充：OAEPWithSHA-256AndMGF1Padding（替代不安全的 PKCS#1 v1.5）</li>
 *   <li>签名算法：SHA256withRSA</li>
 *   <li>密钥长度：默认 2048-bit，支持 3072/4096</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class RSAProvider implements CryptoProvider {

    static final String KEY_ALGORITHM = "RSA";
    private static final String CIPHER_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
    static final int DEFAULT_KEY_SIZE = 2048;

    /**
     * OAEP 开销公式：2 * hashLen（SHA-256=32字节）+ 2
     */
    private static final int OAEP_OVERHEAD = 66;

    @Override
    public byte[] encrypt(byte[] data, Key key) throws Exception {
        if (!(key instanceof PublicKey)) {
            throw new IllegalArgumentException("RSA encrypt requires a PublicKey");
        }
        return processCipher(Cipher.ENCRYPT_MODE, data, key,
                ((RSAKey) key).getModulus().bitLength() / 8 - OAEP_OVERHEAD);
    }

    @Override
    public byte[] decrypt(byte[] data, Key key) throws Exception {
        if (!(key instanceof PrivateKey)) {
            throw new IllegalArgumentException("RSA decrypt requires a PrivateKey");
        }
        return processCipher(Cipher.DECRYPT_MODE, data, key,
                ((RSAKey) key).getModulus().bitLength() / 8);
    }

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

    private static byte[] processCipher(int mode, byte[] data, Key key, int blockSize) throws Exception {
        Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORM);
        cipher.init(mode, key);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int inputLen = data.length;
        int offset = 0;
        int i = 0;
        while (inputLen - offset > 0) {
            int len = Math.min(inputLen - offset, blockSize);
            byte[] cache = cipher.doFinal(data, offset, len);
            out.write(cache);
            i++;
            offset = i * blockSize;
        }
        return out.toByteArray();
    }
}
