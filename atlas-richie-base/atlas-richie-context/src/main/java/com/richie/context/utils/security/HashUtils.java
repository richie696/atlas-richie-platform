package com.richie.context.utils.security;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Hash值计算工具类
 *
 * @author richie696
 * @version 1.1
 * @since 2019/04/15
 */
@Slf4j
public class HashUtils {

    private HashUtils() {
    }

    /**
     * 使用Murmur3哈希算法生成指定消息HashCode的方法
     *
     * @param message  需要生成Hash值的字符串
     * @param seed 自定义种子
     * @return 返回Hash值
     */
    public static String hashcode(byte[] message, int seed) {
        HashFunction murmur3 = Hashing.murmur3_32_fixed();
        HashCode murmur3HashCode = murmur3.hashString(
                new String(message) + "@" + seed, StandardCharsets.UTF_8);
        return murmur3HashCode.toString();
    }

    /**
     * 使用Murmur3哈希算法生成指定消息HashCode的方法
     *
     * @param msg  需要生成Hash值的字符串
     * @param seed 自定义种子
     * @return 返回Hash值
     */
    public static String hashcode(String msg, int seed) {
        HashFunction murmur3 = Hashing.murmur3_32_fixed();
        HashCode murmur3HashCode = murmur3.hashString(
                msg + "@" + seed, StandardCharsets.UTF_8);
        return murmur3HashCode.toString();
    }

    /**
     * 生成指定消息的MD5码的方法
     *
     * @param message 需要生成MD5值的字符串
     * @return 返回消息的MD5码
     */
    public static String md5(byte[] message) {
        HashFunction function = Hashing.md5();
        HashCode hashCode = function.hashBytes(message);
        return hashCode.toString();
    }

    /**
     * 生成指定消息的MD5码的方法
     *
     * @param message 需要生成MD5值的字符串
     * @return 返回消息的MD5码
     */
    public static String md5(String message) {
        HashFunction function = Hashing.md5();
        HashCode hashCode = function.hashString(message, Charset.defaultCharset());
        return hashCode.toString();
    }

    /**
     * 生成指定消息的SHA256码的方法
     *
     * @param message 需要生成SHA256值的字符串
     * @return 返回消息的SHA256码
     */
    public static String sha256(byte[] message) {
        HashFunction function = Hashing.sha256();
        HashCode hashCode = function.hashBytes(message);
        return hashCode.toString();
    }

    /**
     * 生成指定消息的SHA256码的方法
     *
     * @param message 需要生成SHA256值的字符串
     * @return 返回消息的SHA256码
     */
    public static String sha256(String message) {
        HashFunction function = Hashing.sha256();
        HashCode hashCode = function.hashString(message, Charset.defaultCharset());
        return hashCode.toString();
    }

    /**
     * 生成指定消息的SHA512码的方法
     *
     * @param message 需要生成SHA512值的字符串
     * @return 返回消息的SHA512码
     */
    public static byte[] sha512(byte[] message) {
        HashFunction function = Hashing.sha512();
        HashCode hashCode = function.hashBytes(message);
        return hashCode.asBytes();
    }

    /**
     * 生成指定消息的SHA512码的方法
     *
     * @param message 需要生成SHA512值的字符串
     * @return 返回消息的SHA512码
     */
    public static byte[] sha512(String message) {
        HashFunction function = Hashing.sha512();
        HashCode hashCode = function.hashString(message, Charset.defaultCharset());
        return hashCode.asBytes();
    }

    /**
     * 生成指定消息的SHA512码的方法
     *
     * @param message 需要生成SHA512值的字符串
     * @return 返回消息的SHA512码
     */
    public static String sha512WithString(byte[] message) {
        HashFunction function = Hashing.sha512();
        HashCode hashCode = function.hashBytes(message);
        return hashCode.toString();
    }

    /**
     * 生成指定消息的SHA512码的方法
     *
     * @param message 需要生成SHA512值的字符串
     * @return 返回消息的SHA512码
     */
    public static String sha512WithString(String message) {
        HashFunction function = Hashing.sha512();
        HashCode hashCode = function.hashString(message, Charset.defaultCharset());
        return hashCode.toString();
    }

    /**
     * 生成指定消息的Base64格式SHA512值的方法
     *
     * @param message 需要生成的字符串
     * @return 返回BASE64格式的SHA512值
     */
    public static String sha512WithBase64(byte[] message) {
        try {
            return Base64.getEncoder().encodeToString(sha512(message));
        } catch (Exception e) {
            log.error(String.format("Do sha512 error! str = %s", new String(message)), e);
            return null;
        }
    }

    /**
     * 生成指定消息的Base64格式SHA512值的方法
     *
     * @param message 需要生成的字符串
     * @return 返回BASE64格式的SHA512값
     */
    public static String sha512WithBase64(String message) {
        try {
            return Base64.getEncoder().encodeToString(sha512(message));
        } catch (Exception e) {
            log.error(String.format("Do sha512 error! str = %s", message), e);
            return null;
        }
    }
}
