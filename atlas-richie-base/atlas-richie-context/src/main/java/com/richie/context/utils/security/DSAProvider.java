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
