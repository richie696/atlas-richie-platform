package com.richie.component.mfa.validation.engine;

import com.richie.component.mfa.core.config.MfaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA512Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

/**
 * TOTP验证引擎
 * <p>
 * 职责：TOTP验证码生成和验证
 * <p>
 * <b>密钥管理</b>：密钥从 KMS（密钥管理系统）检索，已经是明文格式，无需解密
 *
 * @author richie696
 * @since 5.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TotpValidationEngine {

    /** MFA 统一配置，用于读取 TOTP 时间窗口、算法、位数等 */
    private final MfaProperties properties;

    /**
     * 验证TOTP验证码
     * <p>
     * 验证逻辑：
     * <ol>
     *   <li>验证密钥是否为空</li>
     *   <li>计算当前时间步长（使用配置中的 timeWindow）</li>
     *   <li>检查±window个时间窗口内的验证码</li>
     *   <li>如果任一窗口的验证码匹配，返回 true</li>
     * </ol>
     * <p>
     * <b>注意</b>：密钥从 KMS 检索时已经是明文格式，无需解密
     *
     * @param plainSecret 明文密钥（从 KMS 检索，Base32编码）
     * @param code        用户输入的验证码（6位数字）
     * @param userId      用户ID（用于日志记录）
     * @param tenantId   租户ID（用于日志记录，可选）
     * @param window     时间窗口容错（±window，例如 window=1 表示允许前后1个时间窗口）
     * @param algorithm  算法（SHA1、SHA256、SHA512，如果为null或空则从配置中读取默认算法）
     * @return 验证结果
     * <ul>
     *   <li>{@code true}：验证码正确</li>
     *   <li>{@code false}：验证码错误或密钥为空</li>
     * </ul>
     */
    public boolean verifyCode(String plainSecret, String code,
                             String userId, String tenantId, int window, String algorithm) {
        try {
            // 调试日志：记录验证参数
            log.info("=== TOTP验证调试信息 ===");
            log.info("userId: {}, tenantId: {}, code: {}, window: {}, algorithm: {}",
                userId, tenantId, code, window, algorithm);
            log.info("plainSecret (前50字符): {}...",
                plainSecret != null && plainSecret.length() > 50
                    ? plainSecret.substring(0, 50)
                    : plainSecret);
            log.info("plainSecret (完整): {}", plainSecret);

            // 验证密钥是否为空
            if (StringUtils.isBlank(plainSecret)) {
                log.error("密钥为空，userId: {}, tenantId: {}", userId, tenantId);
                return false;
            }

            // 标准化算法名称（如果为null或空，从配置中读取默认算法）
            String normalizedAlgorithm = normalizeAlgorithm(algorithm);
            log.info("标准化后的算法: {}", normalizedAlgorithm);

            // 计算当前时间步长（RFC 6238：使用 Unix 纪元秒 UTC）
            int timeStep = properties.getTotp().getTimeWindow();
            long unixTimeSeconds = Instant.now().getEpochSecond();
            long currentTimeStep = unixTimeSeconds / timeStep;
            log.info("时间步长: {}, 当前时间步: {}, 当前时间戳(秒, UTC): {}",
                timeStep, currentTimeStep, unixTimeSeconds);

            // 检查±window个时间窗口
            for (int i = -window; i <= window; i++) {
                long testTimeStep = currentTimeStep + i;
                String generatedCode = generateCode(plainSecret, testTimeStep,
                    properties.getTotp().getCodeLength(), normalizedAlgorithm);
                log.info("时间窗口偏移: {}, 时间步: {}, 生成的验证码: {}, 用户输入: {}, 匹配: {}",
                    i, testTimeStep, generatedCode, code, generatedCode.equals(code));
                if (generatedCode.equals(code)) {
                    log.info("验证码匹配成功！");
                    log.info("========================");
                    return true;
                }
            }

            log.warn("所有时间窗口的验证码都不匹配");
            log.info("========================");
            return false;
        } catch (Exception e) {
            log.error("TOTP验证异常，userId: {}, tenantId: {}, algorithm: {}", userId, tenantId, algorithm, e);
            return false;
        }
    }

    /**
     * 验证TOTP验证码（重载方法，支持传入 period 和 digits）
     * <p>
     * 此方法用于从数据库读取的 MfaUserInfo 中获取 period 和 digits，确保与二维码生成时使用的参数一致
     * <p>
     * <b>注意</b>：密钥从 KMS 检索时已经是明文格式，无需解密
     *
     * @param plainSecret 明文密钥（从 KMS 检索，Base32编码）
     * @param code        用户输入的验证码（6位数字）
     * @param userId      用户ID（用于日志记录）
     * @param tenantId   租户ID（用于日志记录，可选）
     * @param window     时间窗口容错（±window，例如 window=1 表示允许前后1个时间窗口）
     * @param algorithm  算法（SHA1、SHA256、SHA512，如果为null或空则从配置中读取默认算法）
     * @param period     时间窗口周期（秒，必须与二维码生成时使用的 period 一致）
     * @param digits     验证码位数（必须与二维码生成时使用的 digits 一致）
     * @return 验证结果
     */
    public boolean verifyCode(String plainSecret, String code,
                             String userId, String tenantId, int window, String algorithm,
                             int period, int digits) {
        try {
            // 调试日志：记录验证参数
            log.info("=== TOTP验证调试信息（使用数据库参数）===");
            log.info("userId: {}, tenantId: {}, code: {}, window: {}, algorithm: {}, period: {}, digits: {}",
                userId, tenantId, code, window, algorithm, period, digits);
            log.info("plainSecret (完整): {}", plainSecret);

            // 验证密钥是否为空
            if (StringUtils.isBlank(plainSecret)) {
                log.error("密钥为空，userId: {}, tenantId: {}", userId, tenantId);
                return false;
            }

            // 标准化算法名称（如果为null或空，从配置中读取默认算法）
            String normalizedAlgorithm = normalizeAlgorithm(algorithm);
            log.info("标准化后的算法: {}", normalizedAlgorithm);

            // 计算当前时间步长（RFC 6238：使用 Unix 纪元秒 UTC，与 period 一致）
            long unixTimeSeconds = Instant.now().getEpochSecond();
            long currentTimeStep = unixTimeSeconds / period;
            log.info("时间窗口周期: {} 秒, 当前时间步: {}, 当前时间戳(秒, UTC): {}",
                period, currentTimeStep, unixTimeSeconds);

            // 检查±window个时间窗口
            for (int i = -window; i <= window; i++) {
                long testTimeStep = currentTimeStep + i;
                String generatedCode = generateCode(plainSecret, testTimeStep, digits, normalizedAlgorithm);
                log.info("时间窗口偏移: {}, 时间步: {}, 生成的验证码: {}, 用户输入: {}, 匹配: {}",
                    i, testTimeStep, generatedCode, code, generatedCode.equals(code));
                if (generatedCode.equals(code)) {
                    log.info("验证码匹配成功！");
                    log.info("========================");
                    return true;
                }
            }

            log.warn("所有时间窗口的验证码都不匹配");
            log.info("========================");
            return false;
        } catch (Exception e) {
            log.error("TOTP验证异常，userId: {}, tenantId: {}, algorithm: {}", userId, tenantId, algorithm, e);
            return false;
        }
    }

    /**
     * 验证TOTP验证码（重载方法，兼容旧代码）
     * <p>
     * 此方法为了向后兼容，如果未传入算法，则从配置中读取默认算法
     *
     * @param plainSecret 明文密钥（从 KMS 检索，Base32编码）
     * @param code        用户输入的验证码（6位数字）
     * @param userId      用户ID（用于日志记录）
     * @param tenantId   租户ID（用于日志记录，可选）
     * @param window     时间窗口容错（±window，例如 window=1 表示允许前后1个时间窗口）
     * @return 验证结果
     */
    public boolean verifyCode(String plainSecret, String code,
                             String userId, String tenantId, int window) {
        return verifyCode(plainSecret, code, userId, tenantId, window, null);
    }

    /**
     * 生成TOTP验证码
     * <p>
     * 根据 RFC 6238 标准生成 TOTP 验证码：
     * <ol>
     *   <li>Base32 解码密钥</li>
     *   <li>将时间步长转换为 8 字节数组（大端序）</li>
     *   <li>计算 HMAC（支持 SHA1、SHA256、SHA512）</li>
     *   <li>动态截取（Dynamic Truncation）</li>
     *   <li>取模并格式化为指定位数的字符串</li>
     * </ol>
     *
     * @param secret   Base32编码的密钥（明文）
     * @param timeStep 时间步长（当前时间戳 / 时间窗口）
     * @param digits   验证码位数（通常为 6）
     * @param algorithm 算法（SHA1、SHA256、SHA512，如果为null或空则从配置中读取默认算法）
     * @return TOTP 验证码（指定位数的数字字符串，例如 "123456"）
     * @throws RuntimeException 如果 Base32 解码失败或生成过程异常
     */
    private String generateCode(String secret, long timeStep, int digits, String algorithm) {
        try {
            // Base32解码
            byte[] key = base32Decode(secret);
            log.debug("Base32解码后的密钥长度: {} 字节", key.length);

            // 时间步长转换为8字节数组（大端序）
            byte[] data = ByteBuffer.allocate(8)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(timeStep)
                .array();
            log.debug("时间步长 {} 转换为字节数组: {}", timeStep, java.util.Arrays.toString(data));

            // 标准化算法名称（如果为null或空，默认使用SHA1）
            String normalizedAlgorithm = normalizeAlgorithm(algorithm);

            // 根据算法选择对应的HMAC实现
            Mac hmac = switch (normalizedAlgorithm) {
                case "SHA256" -> new HMac(new SHA256Digest());
                case "SHA512" -> new HMac(new SHA512Digest());
                default -> new HMac(new SHA1Digest());
            };

            // 计算HMAC
            hmac.init(new KeyParameter(key));
            hmac.update(data, 0, data.length);
            byte[] hmacResult = new byte[hmac.getMacSize()];
            hmac.doFinal(hmacResult, 0);
            log.debug("HMAC结果长度: {} 字节, 算法: {}", hmacResult.length, normalizedAlgorithm);

            // 动态截取
            int offset = hmacResult[hmacResult.length - 1] & 0xf;
            int binary = ((hmacResult[offset] & 0x7f) << 24) |
                        ((hmacResult[offset + 1] & 0xff) << 16) |
                        ((hmacResult[offset + 2] & 0xff) << 8) |
                        (hmacResult[offset + 3] & 0xff);
            log.debug("动态截取 - offset: {}, binary: {}", offset, binary);

            int otp = binary % ((int) Math.pow(10, digits));
            String result = String.format("%0" + digits + "d", otp);
            log.debug("生成的TOTP验证码: {} (binary: {}, digits: {})", result, binary, digits);
            return result;
        } catch (Exception e) {
            log.error("生成TOTP验证码失败，algorithm: {}, secret: {}, timeStep: {}", algorithm, secret, timeStep, e);
            throw new RuntimeException("生成TOTP验证码失败", e);
        }
    }

    /**
     * Base32解码
     * <p>
     * 使用 Apache Commons Codec 的 Base32 解码器将 Base32 编码的字符串解码为字节数组
     *
     * @param encoded Base32编码的字符串
     * @return 解码后的字节数组
     * @throws RuntimeException 如果解码失败
     */
    private byte[] base32Decode(String encoded) {
        try {
            Base32 base32 = new Base32();
            // Base32 解码会自动处理填充字符，即使输入不包含填充也能正确解码
            byte[] decoded = base32.decode(encoded);
            log.debug("Base32解码 - 输入: {}, 输入长度: {}, 输出长度: {} 字节",
                encoded, encoded.length(), decoded.length);
            return decoded;
        } catch (Exception e) {
            log.error("Base32解码失败，encoded: {}, 长度: {}", encoded, encoded != null ? encoded.length() : 0, e);
            throw new RuntimeException("Base32解码失败: %s".formatted(e.getMessage()), e);
        }
    }


    /**
     * 标准化算法名称
     * <p>
     * 将算法名称转换为标准格式（SHA1、SHA256、SHA512）
     * <p>
     * 支持的输入格式：
     * <ul>
     *   <li>SHA1、SHA256、SHA512（标准格式）</li>
     *   <li>HmacSHA1、HmacSHA256、HmacSHA512（带Hmac前缀）</li>
     *   <li>null 或空字符串（从配置中读取默认算法）</li>
     * </ul>
     * <p>
     * 算法优先级：
     * <ol>
     *   <li>如果传入的 algorithm 不为空，使用传入的算法</li>
     *   <li>如果传入的 algorithm 为空，从配置中读取默认算法（properties.getTotp().getAlgorithm() 或 properties.getTotp().getDefaultAlgorithm()）</li>
     *   <li>如果配置中也没有，则使用 SHA1（最后的兜底）</li>
     * </ol>
     *
     * @param algorithm 算法名称（可能包含 "Hmac" 前缀，如 "HmacSHA256"）
     * @return 标准化后的算法名称（SHA1、SHA256、SHA512）
     */
    private String normalizeAlgorithm(String algorithm) {
        // 如果传入的算法不为空，先标准化处理
        if (algorithm != null && !algorithm.isEmpty()) {
            // 移除 "Hmac" 前缀（如果存在）并转换为大写
            String normalized = algorithm.replaceFirst("^Hmac", "").toUpperCase();
            // 验证是否为支持的算法
            if ("SHA1".equals(normalized) || "SHA256".equals(normalized) || "SHA512".equals(normalized)) {
                return normalized;
            }
            // 如果不匹配，记录警告并使用配置中的默认算法
            log.warn("不支持的算法: {}，使用配置中的默认算法", algorithm);
        }

        // 从配置中读取默认算法（优先使用 algorithm，如果没有则使用 defaultAlgorithm）
        String defaultAlgorithm = properties.getTotp().getAlgorithm();
        if (defaultAlgorithm == null || defaultAlgorithm.isEmpty()) {
            defaultAlgorithm = properties.getTotp().getAlgorithm();
        }
        if (defaultAlgorithm == null || defaultAlgorithm.isEmpty()) {
            // 最后的兜底：使用 SHA1（RFC 6238 标准）
            log.debug("配置中未设置默认算法，使用 SHA1（RFC 6238 标准）");
            return "SHA1";
        }

        // 标准化配置中的默认算法
        String normalized = defaultAlgorithm.replaceFirst("^Hmac", "").toUpperCase();
        if ("SHA1".equals(normalized) || "SHA256".equals(normalized) || "SHA512".equals(normalized)) {
            return normalized;
        }

        // 如果配置中的默认算法也不合法，使用 SHA1 兜底
        log.warn("配置中的默认算法不合法: {}，使用 SHA1（RFC 6238 标准）", defaultAlgorithm);
        return "SHA1";
    }
}
