package com.richie.component.mfa.management.manager;

import com.richie.component.mfa.core.config.MfaProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 二维码管理器
 * <p>
 * 职责：生成TOTP二维码URL
 *
 * @author richie696
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QrCodeManager {

    /**
     * MFA统一配置属性
     */
    private final MfaProperties properties;

    /**
     * 生成TOTP二维码URL（otpauth://格式）
     * <p>
     * 此方法只生成URL字符串，不生成二维码图片。
     * <p>
     * 设计原因：
     * <ul>
     *   <li>职责分离：后端负责生成符合TOTP标准的URL，前端负责将URL转换为二维码图片</li>
     *   <li>减少依赖：后端无需引入图片生成库（如ZXing），降低组件复杂度</li>
     *   <li>灵活性：前端可以控制二维码样式、尺寸、容错级别等，支持不同前端框架的二维码库</li>
     *   <li>性能优化：后端只返回字符串，传输数据小；图片生成在客户端，不占用服务器资源</li>
     * </ul>
     * <p>
     * 使用流程：
     * <ol>
     *   <li>后端：生成 otpauth:// URL（本方法）</li>
     *   <li>前端：接收 URL</li>
     *   <li>前端：使用二维码库（如 qrcode.js、qrcode.react）将 URL 转换为图片</li>
     *   <li>用户：使用 Authenticator 应用（如 Google Authenticator、Microsoft Authenticator）扫描二维码</li>
     * </ol>
     * <p>
     * URL格式（完整版，符合 RFC 6238 和 Google Authenticator Key URI Format）：
     * {@code otpauth://totp/{label}?secret={secret}&issuer={issuer}&algorithm={algorithm}&digits={digits}&period={period}}
     * <p>
     * 如果启用租户，label格式：{@code {issuer}:{tenantId}:{userId}}
     * <p>
     * 如果未启用租户，label格式：{@code {issuer}:{userId}}
     * <p>
     * 参数说明：
     * <ul>
     *   <li>{@code secret}：Base32编码的密钥（必填）</li>
     *   <li>{@code issuer}：发行方名称（必填），实际在 URL 中会追加用户登录名，格式：{@code {issuer} {username}}</li>
     *   <li>{@code algorithm}：HMAC算法，可选值：SHA1、SHA256、SHA512（默认：从配置中读取）</li>
     *   <li>{@code digits}：验证码位数，可选值：6、8（默认：6）</li>
     *   <li>{@code period}：时间窗口（秒），默认：30</li>
     * </ul>
     *
     * @param tenantId 租户ID（可选，如果未启用租户则为 null）
     * @param userId   用户ID（必填，业务系统User表的主键ID）
     * @param secret   密钥（明文，Base32编码）
     * @param issuer   发行方名称（必填，例如 "Rydeen Platform"），实际在 URL 中会追加用户登录名，格式：{@code {issuer} {username}}
     * @param algorithm HMAC算法（SHA1、SHA256、SHA512，如果为null或空则从配置中读取默认算法）
     * @param digits   验证码位数（6 或 8，默认：6）
     * @param period   时间窗口（秒，默认：30）
     * @return TOTP二维码URL（otpauth://格式，已URL编码）
     * @throws RuntimeException 如果URL编码失败
     */
    public String generateQrCodeUrl(String tenantId, String userId, String username, String secret, String issuer,
                                     String algorithm, int digits, int period) {
        // otpauth://totp/{label}?secret={secret}&issuer={issuer}&algorithm={algorithm}&digits={digits}&period={period}
        // 如果启用租户，label可以包含租户信息，否则只使用userId
        String label = (tenantId != null && !tenantId.isEmpty())
            ? "%s:%s:%s".formatted(issuer, tenantId, userId)
            : "%s:%s".formatted(issuer, userId);
        
        // issuer 后面追加用户登录名，格式：{issuer} {username}
        String issuerWithUsername = "%s %s".formatted(issuer, username);

        try {
            String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8);
            String encodedIssuer = URLEncoder.encode(issuerWithUsername, StandardCharsets.UTF_8);
            // 密钥也需要 URL 编码（Base32 编码可能包含特殊字符如 /、= 等）
            String encodedSecret = URLEncoder.encode(secret, StandardCharsets.UTF_8);
            // 确保 algorithm 参数使用标准格式（SHA1/SHA256/SHA512）
            String normalizedAlgorithm = normalizeAlgorithm(algorithm);
            // 构建完整的 otpauth:// URL，包含所有必需参数
            // 注意：所有参数值都需要 URL 编码，包括 secret
            String qrCodeUrl = "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=%s&digits=%d&period=%d".formatted(
                encodedLabel, encodedSecret, encodedIssuer, normalizedAlgorithm, digits, period);

            // 输出生成的二维码 URL 到控制台，便于调试
            log.info("=== MFA 二维码 URL（调试信息）===");
            log.info("原始 label: {}", label);
            log.info("编码后 label: {}", encodedLabel);
            log.info("原始 issuer: {}", issuer);
            log.info("用户登录名 (username): {}", username);
            log.info("issuer (追加用户登录名后): {}", issuerWithUsername);
            log.info("编码后 issuer: {}", encodedIssuer);
            log.info("原始密钥 (secret): {}", secret);
            log.info("编码后密钥 (secret): {}", encodedSecret);
            log.info("算法 (algorithm): {} -> {}", algorithm, normalizedAlgorithm);
            log.info("位数 (digits): {}", digits);
            log.info("时间窗口 (period): {}", period);
            log.info("完整 otpauth:// URL: {}", qrCodeUrl);
            log.info("===================================");

            return qrCodeUrl;
        } catch (Exception e) {
            log.error("生成二维码URL失败", e);
            throw new RuntimeException("生成二维码URL失败", e);
        }
    }

    /**
     * 标准化算法名称
     * <p>
     * 将算法名称转换为 otpauth:// URL 标准格式
     * <p>
     * otpauth:// URL 中的 algorithm 参数值：
     * <ul>
     *   <li>SHA1 → SHA1</li>
     *   <li>SHA256 → SHA256</li>
     *   <li>SHA512 → SHA512</li>
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
