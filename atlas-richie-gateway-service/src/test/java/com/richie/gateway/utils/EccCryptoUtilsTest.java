package com.richie.gateway.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EccCryptoUtils 测试类
 * <p>
 * 所有方法均为纯静态加密工具，无任何外部依赖或 Spring 上下文，直接调用即可。
 * 覆盖密钥生成、Base64 序列化/反序列化、ECDH 共享密钥协商、AES-GCM 加解密、密钥对匹配验证。
 */
class EccCryptoUtilsTest {

    @Nested
    @DisplayName("generateKeyPair 生成 ECC 密钥对")
    class GenerateKeyPairTests {

        @Test
        @DisplayName("返回非 null 的 KeyPair，公钥和私钥均不为 null")
        void returnsNonNullKeyPair() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();

            assertThat(keyPair).isNotNull();
            assertThat(keyPair.getPublic()).isNotNull();
            assertThat(keyPair.getPrivate()).isNotNull();
        }

        @Test
        @DisplayName("公钥类型为 EC")
        void publicKeyIsEC() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();

            assertThat(keyPair.getPublic().getAlgorithm()).isEqualTo("EC");
        }

        @Test
        @DisplayName("私钥类型为 EC")
        void privateKeyIsEC() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();

            assertThat(keyPair.getPrivate().getAlgorithm()).isEqualTo("EC");
        }

        @Test
        @DisplayName("每次调用生成不同的密钥对")
        void eachCallGeneratesDistinctKeyPair() throws Exception {
            KeyPair kp1 = EccCryptoUtils.generateKeyPair();
            KeyPair kp2 = EccCryptoUtils.generateKeyPair();

            assertThat(kp1.getPublic()).isNotEqualTo(kp2.getPublic());
            assertThat(kp1.getPrivate()).isNotEqualTo(kp2.getPrivate());
        }
    }

    @Nested
    @DisplayName("publicKeyToBase64 / base64ToPublicKey 往返序列化")
    class PublicKeyBase64RoundTripTests {

        @Test
        @DisplayName("Base64 往返后公钥编码字节一致")
        void roundTripPreservesEncodedBytes() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();
            PublicKey original = keyPair.getPublic();

            String base64 = EccCryptoUtils.publicKeyToBase64(original);
            PublicKey restored = EccCryptoUtils.base64ToPublicKey(base64);

            assertThat(restored.getEncoded()).isEqualTo(original.getEncoded());
        }

        @Test
        @DisplayName("往返后的公钥可正常用于 ECDH 共享密钥生成")
        void restoredPublicKeyUsableForEcdh() throws Exception {
            KeyPair serverKp = EccCryptoUtils.generateKeyPair();
            KeyPair clientKp = EccCryptoUtils.generateKeyPair();

            String base64Pub = EccCryptoUtils.publicKeyToBase64(serverKp.getPublic());
            PublicKey restoredPub = EccCryptoUtils.base64ToPublicKey(base64Pub);

            // 用还原的公钥与客户端私钥协商共享密钥
            SecretKey shared1 = EccCryptoUtils.generateSharedSecret(clientKp.getPrivate(), restoredPub);
            // 对比用服务端原私钥与客户端公钥协商的结果
            SecretKey shared2 = EccCryptoUtils.generateSharedSecret(serverKp.getPrivate(), clientKp.getPublic());

            assertThat(shared1.getEncoded()).isEqualTo(shared2.getEncoded());
        }
    }

    @Nested
    @DisplayName("privateKeyToBase64 / base64ToPrivateKey 往返序列化")
    class PrivateKeyBase64RoundTripTests {

        @Test
        @DisplayName("Base64 往返后私钥编码字节一致")
        void roundTripPreservesEncodedBytes() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();
            PrivateKey original = keyPair.getPrivate();

            String base64 = EccCryptoUtils.privateKeyToBase64(original);
            PrivateKey restored = EccCryptoUtils.base64ToPrivateKey(base64);

            assertThat(restored.getEncoded()).isEqualTo(original.getEncoded());
        }

        @Test
        @DisplayName("往返后的私钥可正常用于 ECDH 共享密钥生成")
        void restoredPrivateKeyUsableForEcdh() throws Exception {
            KeyPair serverKp = EccCryptoUtils.generateKeyPair();
            KeyPair clientKp = EccCryptoUtils.generateKeyPair();

            String base64Priv = EccCryptoUtils.privateKeyToBase64(serverKp.getPrivate());
            PrivateKey restoredPriv = EccCryptoUtils.base64ToPrivateKey(base64Priv);

            SecretKey shared1 = EccCryptoUtils.generateSharedSecret(restoredPriv, clientKp.getPublic());
            SecretKey shared2 = EccCryptoUtils.generateSharedSecret(serverKp.getPrivate(), clientKp.getPublic());

            assertThat(shared1.getEncoded()).isEqualTo(shared2.getEncoded());
        }
    }

    @Nested
    @DisplayName("generateSharedSecret ECDH 共享密钥协商")
    class GenerateSharedSecretTests {

        @Test
        @DisplayName("甲乙双方协商结果一致")
        void bothPartiesProduceSameSharedSecret() throws Exception {
            KeyPair serverKp = EccCryptoUtils.generateKeyPair();
            KeyPair clientKp = EccCryptoUtils.generateKeyPair();

            SecretKey fromServer = EccCryptoUtils.generateSharedSecret(serverKp.getPrivate(), clientKp.getPublic());
            SecretKey fromClient = EccCryptoUtils.generateSharedSecret(clientKp.getPrivate(), serverKp.getPublic());

            assertThat(fromServer.getEncoded()).isEqualTo(fromClient.getEncoded());
        }

        @Test
        @DisplayName("不同密钥对组合产生的共享密钥不同")
        void differentKeyPairsProduceDifferentSecrets() throws Exception {
            KeyPair kp1 = EccCryptoUtils.generateKeyPair();
            KeyPair kp2 = EccCryptoUtils.generateKeyPair();
            KeyPair kp3 = EccCryptoUtils.generateKeyPair();

            SecretKey secret1 = EccCryptoUtils.generateSharedSecret(kp1.getPrivate(), kp2.getPublic());
            SecretKey secret2 = EccCryptoUtils.generateSharedSecret(kp1.getPrivate(), kp3.getPublic());

            assertThat(secret1.getEncoded()).isNotEqualTo(secret2.getEncoded());
        }
    }

    @Nested
    @DisplayName("encrypt / decrypt AES-GCM 加解密往返")
    class EncryptDecryptRoundTripTests {

        @Test
        @DisplayName("加密后解密还原为原始明文")
        void encryptThenDecryptRestoresOriginal() throws Exception {
            KeyPair serverKp = EccCryptoUtils.generateKeyPair();
            KeyPair clientKp = EccCryptoUtils.generateKeyPair();
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(serverKp.getPrivate(), clientKp.getPublic());

            String plaintext = "Hello, ECC-GCM secure world! 中文测试";

            String ciphertext = EccCryptoUtils.encrypt(plaintext, sharedKey);
            assertThat(ciphertext).isNotEqualTo(plaintext);

            String decrypted = EccCryptoUtils.decrypt(ciphertext, sharedKey);
            assertThat(decrypted).isEqualTo(plaintext);
        }

        @Test
        @DisplayName("空字符串加密后解密仍为空字符串（直接返回）")
        void emptyString_returnsUnchanged() throws Exception {
            KeyPair kp = EccCryptoUtils.generateKeyPair();
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(kp.getPrivate(), kp.getPublic());

            assertThat(EccCryptoUtils.encrypt("", sharedKey)).isEmpty();
            assertThat(EccCryptoUtils.decrypt("", sharedKey)).isEmpty();
        }

        @Test
        @DisplayName("null 加密后解密仍为 null（直接返回）")
        void nullInput_returnsNull() throws Exception {
            KeyPair kp = EccCryptoUtils.generateKeyPair();
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(kp.getPrivate(), kp.getPublic());

            assertThat(EccCryptoUtils.encrypt(null, sharedKey)).isNull();
            assertThat(EccCryptoUtils.decrypt(null, sharedKey)).isNull();
        }

        @Test
        @DisplayName("纯 ASCII 与 Unicode 混合内容加解密正确")
        void mixedAsciiUnicode() throws Exception {
            KeyPair kp = EccCryptoUtils.generateKeyPair();
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(kp.getPrivate(), kp.getPublic());

            String content = "User: alice | 订单号: ORD-2025-中文 | emoji: 🎉";
            String ciphertext = EccCryptoUtils.encrypt(content, sharedKey);
            String decrypted = EccCryptoUtils.decrypt(ciphertext, sharedKey);

            assertThat(decrypted).isEqualTo(content);
        }

        @Test
        @DisplayName("加密结果每次不同（随机 IV），但解密结果一致")
        void samePlaintextProducesDifferentCiphertext() throws Exception {
            KeyPair kp = EccCryptoUtils.generateKeyPair();
            SecretKey sharedKey = EccCryptoUtils.generateSharedSecret(kp.getPrivate(), kp.getPublic());
            String plaintext = "Same input";

            String ct1 = EccCryptoUtils.encrypt(plaintext, sharedKey);
            String ct2 = EccCryptoUtils.encrypt(plaintext, sharedKey);

            // 两次加密结果应不同（随机 IV），但都能还原为相同明文
            assertThat(ct1).isNotEqualTo(ct2);
            assertThat(EccCryptoUtils.decrypt(ct1, sharedKey)).isEqualTo(plaintext);
            assertThat(EccCryptoUtils.decrypt(ct2, sharedKey)).isEqualTo(plaintext);
        }
    }

    @Nested
    @DisplayName("validateKeyPair 密钥对匹配验证")
    class ValidateKeyPairTests {

        @Test
        @DisplayName("真实匹配的密钥对验证通过")
        void matchingPair_returnsTrue() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();

            boolean valid = EccCryptoUtils.validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());

            assertThat(valid).isTrue();
        }

        @Test
        @DisplayName("公钥与私钥来自不同密钥对时验证失败")
        void mismatchedPair_returnsFalse() throws Exception {
            KeyPair kp1 = EccCryptoUtils.generateKeyPair();
            KeyPair kp2 = EccCryptoUtils.generateKeyPair();

            // 用 kp2 的公钥 + kp1 的私钥——来自不同对，ECDH 无法协商出相同共享密钥
            boolean valid = EccCryptoUtils.validateKeyPair(kp2.getPublic(), kp1.getPrivate());

            assertThat(valid).isFalse();
        }

        @Test
        @DisplayName("同一密钥对多次验证结果一致")
        void consistentResult() throws Exception {
            KeyPair keyPair = EccCryptoUtils.generateKeyPair();

            boolean first = EccCryptoUtils.validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());
            boolean second = EccCryptoUtils.validateKeyPair(keyPair.getPublic(), keyPair.getPrivate());

            assertThat(first).isEqualTo(second);
        }
    }
}
