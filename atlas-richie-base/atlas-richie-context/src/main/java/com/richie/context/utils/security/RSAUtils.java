package com.richie.context.utils.security;

import org.apache.commons.lang3.StringUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>类描述：RSA公钥/私钥/签名工具类
 * <pre>
 * 字符串格式的密钥在未在特殊说明情况下都为BASE64编码格式
 * 由于非对称加密速度极其缓慢，一般文件不使用它来加密而是使用对称加密，
 * 非对称加密算法可以用来对对称加密的密钥加密，这样保证密钥的安全也就保证了数据的安全。
 *
 * 改动说明：
 *      【修改人：王锦阳 / 2017年8月21日 下午4:08:41 / 版本：1.0】
 *      1. 初始版本创建
 *      【修改人：王锦阳 / 2018年01月17日 下午15:11:20 / 版本：1.1】
 *      1. 优化加/解密逻辑
 *      2. 加/解密数据方法当执行失败时从打印异常堆栈改为返回null值
 *      【修改人：王锦阳 / 2022年12月30日 上午09:49:20 / 版本：1.2】
 *      1. 支持通过传入公/私钥字符串进行加密
 *      【修改人：王锦阳 / 2022年01月26日 上午10:56:10 / 版本：1.3】
 * </pre>
 *
 * <p>【使用示例】
 *     {@code Map<RSAKeyName, RSAKey> keyMap = genKeyPair();}<br>
 *     {@code String publicKey = getPublicKEY(keyMap);}<br>
 *     {@code String privateKey = getPrivateKEY(keyMap);}<br>
 *     {@code System.out.println("公钥：" + publicKey);}<br>
 *     {@code System.out.println("私钥：" + privateKey);}<br>
 * @author 王锦阳
 * @version 1.3
 * @since 2017年8月21日 下午4:08:41
 */
public class RSAUtils {

    private RSAUtils() {
    }

    /**
     * <p>类描述：RSA密钥对名称枚举类
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:29:56 / 版本：1.0】
     * </pre>
     * @author 王锦阳
     * @version 1.0
     * @since 2017年8月21日 下午4:29:56
     */
    public enum RSAKeyName {
        /**
         * 公钥
         */
        PUBLIC_KEY,
        /**
         * 私钥
         */
        PRIVATE_KEY
    }

    /**
     * <p>类描述：签名算法枚举类
     */
    public enum SignatureAlgorithm {
        /**
         * SHA1withRSA算法
         */
        SHA1withRSA,
        /**
         * SHA256withRSA算法
         */
        SHA256withRSA,
        /**
         * MD5withRSA算法
         */
        MD5withRSA
    }

    /**
     * 加密算法RSA
     */
    public static final String KEY_ALGORITHM = "RSA";

    /**
     * 默认签名算法
     */
    public static final SignatureAlgorithm DEFAULT_SIGNATURE_ALGORITHM = SignatureAlgorithm.SHA256withRSA;

    /**
     * RSA最大加密明文大小
     */
    private static final int MAX_ENCRYPT_BLOCK = 117;

    /**
     * RSA最大解密密文大小
     */
    private static final int MAX_DECRYPT_BLOCK = 128;

    /**
     * <p>方法描述：生成密钥对的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:12:38 / 版本：1.0】
     * </pre>
     * @return 返回生成结果
     * @throws Exception 生成密钥对错误时抛出该异常
     */
    public static Map<RSAKeyName, RSAKey> genKeyPair() throws Exception {
        KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(KEY_ALGORITHM);
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        Map<RSAKeyName, RSAKey> keyMap = new HashMap<>(2);
        keyMap.put(RSAKeyName.PUBLIC_KEY, publicKey);
        keyMap.put(RSAKeyName.PRIVATE_KEY, privateKey);
        return keyMap;
    }

    /**
     * <p>方法描述：用私钥对信息生成数字签名的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:13:22 / 版本：1.0】
     * </pre>
     * @param privateKey 私钥
     * @param data 已加密数据的二进制数组
     * @return 返回数字签名
     * @throws Exception 签名过程产生错误时抛出该异常
     */
    public static String sign(String privateKey, byte[] data) throws Exception {
        return sign(DEFAULT_SIGNATURE_ALGORITHM, privateKey, data);
    }

    /**
     * <p>方法描述：用私钥对信息生成数字签名的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:13:22 / 版本：1.0】
     * </pre>
     * @param algorithm 签名算法
     * @param privateKey 私钥
     * @param data 已加密数据的二进制数组
     * @return 返回数字签名
     * @throws Exception 签名过程产生错误时抛出该异常
     * @since 1.3
     */
    public static String sign(SignatureAlgorithm algorithm, String privateKey, byte[] data) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        PrivateKey privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Signature signature = Signature.getInstance(algorithm.name());
        signature.initSign(privateK);
        signature.update(data);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * <p>方法描述：校验数字签名的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:14:32 / 版本：1.0】
     * </pre>
     * @param data 被加密数据的二进制数组
     * @param publicKey 公钥
     * @param sign 字符串签名信息
     * @return 返回校验结果（true：成功，false：失败）
     * @throws Exception 校验过程产生错误时抛出该异常
     */
    public static boolean verify(String publicKey, byte[] data, String sign) throws Exception {
        return verify(DEFAULT_SIGNATURE_ALGORITHM, publicKey, data, sign);
    }

    /**
     * <p>方法描述：校验数字签名的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:14:32 / 版本：1.0】
     * </pre>
     * @param algorithm 签名算法
     * @param data 被加密数据的二进制数组
     * @param publicKey 公钥
     * @param sign 字符串签名信息
     * @return 返回校验结果（true：成功，false：失败）
     * @throws Exception 校验过程产生错误时抛出该异常
     * @since 1.3
     */
    public static boolean verify(SignatureAlgorithm algorithm, String publicKey, byte[] data, String sign) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        PublicKey publicK = keyFactory.generatePublic(keySpec);
        Signature signature = Signature.getInstance(algorithm.name());
        signature.initVerify(publicK);
        signature.update(data);
        return signature.verify(Base64.getDecoder().decode(sign));
    }

    /**
     * <p>方法描述：使用私钥解密的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:15:35 / 版本：1.0】
     * </pre>
     * @param encryptedData 已加密数据的二进制数组
     * @param privateKey 私钥
     * @return 返回解密后的结果
     * @throws Exception 解密过程产生错误时抛出该异常
     */
    public static byte[] decryptByPrivateKEY(byte[] encryptedData, String privateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, privateK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 对数据分段解密
        decryptLogic(encryptedData, cipher, out, MAX_DECRYPT_BLOCK);
        byte[] decryptedData = out.toByteArray();
        out.close();
        return decryptedData;
    }

    private static void decryptLogic(byte[] encryptedData, Cipher cipher, ByteArrayOutputStream out, int block) throws IllegalBlockSizeException, BadPaddingException {
        int inputLen = encryptedData.length;
        byte[] cache;
        int offSet = 0;
        int i = 0;
        while (inputLen - offSet > 0) {
            if (inputLen - offSet > block) {
                cache = cipher.doFinal(encryptedData, offSet, block);
            } else {
                cache = cipher.doFinal(encryptedData, offSet, inputLen - offSet);
            }
            out.write(cache, 0, cache.length);
            i++;
            offSet = i * block;
        }
    }

    /**
     * <p>方法描述：使用公钥解密的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:16:34 / 版本：1.0】
     * </pre>
     * @param encryptedData 已加密数据的二进制数组
     * @param publicKey 公钥
     * @return 返回解密后的结果
     * @throws Exception 解密过程产生错误时抛出该异常
     */
    public static byte[] decryptByPublicKEY(byte[] encryptedData, String publicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.DECRYPT_MODE, publicK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        decryptLogic(encryptedData, cipher, out, MAX_DECRYPT_BLOCK);
        byte[] decryptedData = out.toByteArray();
        out.close();
        return decryptedData;
    }

    /**
     * <p>方法描述：利用公钥加密数据的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:11:08 / 版本：1.0】
     * </pre>
     * @param data 源数据
     * @param publicKey 公钥
     * @return 返回加密结果字符串
     * @throws Exception 加密过程产生错误时抛出该异常
     */
    public static byte[] encryptByPublicKEY(byte[] data, String publicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(publicKey);
        X509EncodedKeySpec x509KeySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key publicK = keyFactory.generatePublic(x509KeySpec);
        // 对数据加密
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 对数据分段加密
        decryptLogic(data, cipher, out, MAX_ENCRYPT_BLOCK);
        byte[] encryptedData = out.toByteArray();
        out.close();
        return encryptedData;
    }

    /**
     * <p>方法描述：利用私钥加密数据的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:11:08 / 版本：1.0】
     * </pre>
     * @param data 源数据
     * @param privateKey 私钥
     * @return 返回加密结果字符串
     * @throws Exception 加密过程产生错误时抛出该异常
     */
    public static byte[] encryptByPrivateKEY(byte[] data, String privateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(privateKey);
        PKCS8EncodedKeySpec pkcs8KeySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Key privateK = keyFactory.generatePrivate(pkcs8KeySpec);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, privateK);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 对数据分段加密
        decryptLogic(data, cipher, out, MAX_ENCRYPT_BLOCK);
        byte[] encryptedData = out.toByteArray();
        out.close();
        return encryptedData;
    }

    /**
     * <p>方法描述：获取私钥的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:18:30 / 版本：1.0】
     * </pre>
     * @param keyMap 密钥对集合
     * @return 返回私钥对象
     */
    public static String getPrivateKEY(Map<RSAKeyName, RSAKey> keyMap) {
        Key key = (Key) keyMap.get(RSAKeyName.PRIVATE_KEY);
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * <p>方法描述：获取公钥的方法
     * <pre>
     * 改动说明：
     *      【修改人：王锦阳 / 2017年8月21日 下午4:18:30 / 版本：1.0】
     * </pre>
     * @param keyMap 密钥对集合
     * @return 返回公钥对象
     */
    public static String getPublicKEY(Map<RSAKeyName, RSAKey> keyMap) {
        Key key = (Key) keyMap.get(RSAKeyName.PUBLIC_KEY);
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /**
     * <p>方法描述：服务端进行公钥加密的方法
     * <pre>
     * 改动说明：
     *
     *      【修改人：王锦阳 / 2017年8月21日 下午4:26:04 / 版本：1.0】
     *      1. 初始方法创建
     *
     *      【修改人：王锦阳 / 2018年01月17日 下午15:11:20 / 版本：1.1】
     *      1. 加/解密数据方法当执行失败时从打印异常堆栈改为返回null值
     *
     * </pre>
     * @param data 源数据
     * @param publicKey 公钥
     * @return 返回加密后的结果<b style="color: red">（如果加密失败则返回null，即表示源数据或公钥错误。）</b>
     */
    public static String encryptedDataOnJava(String data, String publicKey) {
        String encryptedString;
        try {
            encryptedString = Base64.getEncoder().encodeToString(encryptByPublicKEY(data.getBytes(), publicKey));
        } catch (Exception e) {
            return null;
        }
        if (StringUtils.isBlank(encryptedString)) {
            return null;
        }
        return encryptedString;
    }

    /**
     * <p>方法描述：服务端进行私钥解密的方法
     * <pre>
     * 改动说明：
     *
     *      【修改人：王锦阳 / 2017年8月21日 下午4:26:04 / 版本：1.0】
     *      初始方法创建
     *
     *      【修改人：王锦阳 / 2018年01月17日 下午15:11:20 / 版本：1.1】
     *      1. 加/解密数据方法当执行失败时从打印异常堆栈改为返回null值
     *
     * </pre>
     * @param data 加密数据
     * @param privateKey 私钥
     * @return 返回解密后的结果<b style="color: red">（如果解密失败则返回null，即表示加密串错误。）</b>
     */
    public static String decryptDataOnJava(String data, String privateKey) {
        String decryptString;
        try {
            byte[] rs = Base64.getDecoder().decode(data);
            decryptString = new String(RSAUtils.decryptByPrivateKEY(rs, privateKey), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
        if (StringUtils.isBlank(decryptString)) {
            return null;
        }
        return decryptString;
    }

}
