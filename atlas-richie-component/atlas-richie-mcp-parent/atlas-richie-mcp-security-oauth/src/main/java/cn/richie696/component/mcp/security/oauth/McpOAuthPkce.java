package cn.richie696.component.mcp.security.oauth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * OAuth 2.1 PKCE S256 helper.
 *
 * <p>封装 Proof Key for Code Exchange（RFC 7636）的三个最小操作：生成 verifier、由 verifier
 * 派生 S256 challenge、常量时间校验 verifier ↔ challenge 的对应关系。所有方法均为静态，
 * 不可实例化。该 helper 不持久化任何状态，单实例可在多线程间安全复用（{@link SecureRandom}
 * 是线程安全的）。</p>
 *
 * <p>为什么只支持 S256：OAuth 2.1 §4.1.1 已明确弃用 {@code plain} 模式（verifier 直接作为
 * challenge），因为它无法抵御具备读取初始请求能力的攻击者；强制 S256 也是
 * {@link McpOAuthAuthorizationRequest} 拒绝 {@code plain} challenge 的根本依据。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpOAuthPkce {
    private static final SecureRandom RANDOM = new SecureRandom();

    private McpOAuthPkce() {
    }

    /**
     * 生成一个 RFC 7636 §4.1 规定的 PKCE code_verifier。
     *
     * <p>实现细节：使用 32 字节随机输入 + URL-safe Base64 无 padding 编码，
     输出长度恰好为 43 字符，符合 RFC 7636 §4.1（43~128 字符、字符集
     {@code [A-Z][a-z][0-9]-._~}）的要求；{@link SecureRandom} 确保熵源不可预测，
 * 并发调用时无需额外同步。</p>
     *
     * @return 新的不可预测 PKCE verifier
     */
    public static String generateVerifier() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 由 verifier 派生 S256 code_challenge。
     *
     * <p>verifier 必须满足 ASCII 字符集且非空，否则视为上游逻辑错误，构造阶段即 fail-fast。
     {@link MessageDigest#getInstance(String)} 在 JDK 标准发行版中不可能抛出
     {@link java.security.NoSuchAlgorithmException}（SHA-256 是 JCE 强制实现），一旦发生
     说明环境异常，应升级为 {@link IllegalStateException} 让调用方感知而非静默回退。</p>
     *
     * @param verifier 已生成的 verifier（必填，非空）
     * @return URL-safe Base64 无 padding 编码的 SHA-256 摘要
     * @throws IllegalArgumentException 当 verifier 为 null 或 blank 时
     * @throws IllegalStateException 当 SHA-256 算法不可用（理论上不可能，仅作防御）
     */
    public static String challenge(String verifier) {
        if (verifier == null || verifier.isBlank()) {
            throw new IllegalArgumentException("PKCE verifier must not be blank");
        }
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 常量时间校验 verifier 与 challenge 的对应关系。
     *
     * <p>使用 {@link MessageDigest#isEqual(byte[], byte[])} 而非 {@link String#equals(Object)}
     是为了规避计时攻击：字符串 equals 会在首个不匹配字节处返回 true，攻击者可以此
     逐字节爆破 challenge。常量时间比较无论内容是否匹配都消耗相同 CPU 周期，是
     安全敏感场景的标准做法。{@code expectedChallenge} 为 null 时直接返回 false。</p>
     *
     * @param verifier 客户端持有的 verifier
     * @param expectedChallenge 待校验的 challenge（通常由 AS 在 token 交换时回传）
     * @return true 表示 verifier 经 SHA-256 派生后与 expectedChallenge 相同
     */
    public static boolean verify(String verifier, String expectedChallenge) {
        return expectedChallenge != null
                && MessageDigest.isEqual(
                challenge(verifier).getBytes(StandardCharsets.US_ASCII),
                expectedChallenge.getBytes(StandardCharsets.US_ASCII));
    }
}
