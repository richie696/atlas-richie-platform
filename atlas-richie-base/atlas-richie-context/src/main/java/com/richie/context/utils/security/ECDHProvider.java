package com.richie.context.utils.security;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;

/**
 * ECDH 椭圆曲线密钥协商 Provider
 * <p>
 * 符合 NIST SP 800-56A 标准：
 * <ul>
 *   <li>曲线：secp256r1（P-256）</li>
 *   <li>协商算法：ECDH</li>
 *   <li>派生密钥：AES 密钥规范</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2026/06/02
 */
class ECDHProvider implements CryptoProvider {

    private static final String CURVE = "secp256r1";
    private static final String KEY_AGREEMENT = "ECDH";
    private static final String KEY_ALGORITHM = "EC";
    private static final String SYMMETRIC_ALGORITHM = "AES";

    @Override
    public KeyPair generateKeyPair(int keySize) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        kpg.initialize(new ECGenParameterSpec(CURVE));
        return kpg.generateKeyPair();
    }

    @Override
    public SecretKey generateSharedSecret(PrivateKey privateKey, PublicKey publicKey) throws Exception {
        KeyAgreement ka = KeyAgreement.getInstance(KEY_AGREEMENT);
        ka.init(privateKey);
        ka.doPhase(publicKey, true);
        byte[] sharedSecret = ka.generateSecret();
        return new SecretKeySpec(sharedSecret, SYMMETRIC_ALGORITHM);
    }
}
