package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.WebInterceptor;
import com.richie.component.web.core.spi.WebInterceptorChain;
import com.richie.component.web.core.spi.WebRequestContext;
import com.richie.contract.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * 平台防护层 B 组：API 签名校验（README.md §4.8.2 / §5.2 A-5）。
 * <p>
 * 校验三类头：
 * <ul>
 *   <li>{@code X-Timestamp}：Unix 毫秒时间戳，与服务器时钟偏差 &gt; skew 即拒绝</li>
 *   <li>{@code X-Nonce}：唯一随机串，TTL 窗口内重复即拒绝（重放防御）</li>
 *   <li>{@code X-Signature}：HMAC-SHA256(method, path, timestamp, nonce, body)，hex 编码</li>
 * </ul>
 * 命中任意一项失败 → markShortCircuit(denyStatus, body) + 不调 proceed。
 *
 * <h2>Order</h2>
 * <p>{@link #ORDER} = 220：B 组最后跑（在 Anomaly/BruteForce 之后）。
 *
 * <h2>密钥来源</h2>
 * <p>本类只验签，密钥由调用方传入（从 Vault / 配置中心 / DB 加载——业务方决定）。
 *
 * @author richie696
 * @since 2026-07
 */
@Slf4j
public class ApiSignatureInterceptor implements WebInterceptor, Ordered {

    /** 决策事件 attribute key。 */
    public static final String DECISION_ATTRIBUTE = "platform.web.signature-decision";

    /** 拦截器在链中的位置。 */
    public static final int ORDER = 220;

    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_SIGNATURE = "X-Signature";

    private final SecretKeyProvider keyProvider;
    private final NonceCache nonceCache;
    private final long timestampSkewMillis;
    private final int denyStatus;
    private final String denyCode;
    private final String denyMsg;

    public ApiSignatureInterceptor(SecretKeyProvider keyProvider,
                                   NonceCache nonceCache,
                                   long timestampSkewSeconds,
                                   int denyStatus,
                                   String denyCode,
                                   String denyMsg) {
        this.keyProvider = keyProvider;
        this.nonceCache = nonceCache;
        this.timestampSkewMillis = timestampSkewSeconds * 1000L;
        this.denyStatus = denyStatus;
        this.denyCode = denyCode;
        this.denyMsg = denyMsg;
    }

    @Override
    public void intercept(WebRequestContext ctx, WebInterceptorChain chain) throws Exception {
        if (isGatewayBypassed(ctx)) {
            chain.proceed(ctx);
            return;
        }
        String key = ctx.clientKey();
        if (key == null || key.isBlank()) {
            chain.proceed(ctx);
            return;
        }

        String tsStr = ctx.header(HEADER_TIMESTAMP);
        if (tsStr == null) {
            deny(ctx, "missing_header", key);
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(tsStr);
        } catch (NumberFormatException e) {
            deny(ctx, "bad_timestamp", key);
            return;
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - ts) > timestampSkewMillis) {
            deny(ctx, "timestamp_skew", key);
            return;
        }

        String nonce = ctx.header(HEADER_NONCE);
        if (nonce == null) {
            deny(ctx, "missing_header", key);
            return;
        }
        if (!nonceCache.putIfAbsent(nonce)) {
            deny(ctx, "nonce_replay", key);
            return;
        }

        String sig = ctx.header(HEADER_SIGNATURE);
        if (sig == null) {
            deny(ctx, "missing_header", key);
            return;
        }

        byte[] secret = keyProvider.secretFor(key);
        if (secret == null || secret.length == 0) {
            deny(ctx, "no_secret", key);
            return;
        }

        String canonical = canonicalize(ctx, ts, nonce);
        String expected = hmacSha256Hex(secret, canonical);
        if (!constantTimeEquals(expected, sig)) {
            deny(ctx, "signature_mismatch", key);
            return;
        }

        chain.proceed(ctx);
    }

    private void deny(WebRequestContext ctx, String reason, String key) {
        String body = renderBody(reason);
        ctx.setAttribute(DECISION_ATTRIBUTE,
                Map.of("type", "signature", "reason", reason, "key", key));
        ctx.markShortCircuit(denyStatus, body);
        log.info("Signature deny: reason={} key={} path={}", reason, key, ctx.path());
    }

    private String renderBody(String reason) {
        String msg = denyMsg.replace("{reason}", reason);
        return ApiResult.error(denyCode, msg).toJson();
    }

    private static String canonicalize(WebRequestContext ctx, long ts, String nonce) {
        return ctx.method()
                + "\n" + ctx.path()
                + "\n" + ts
                + "\n" + nonce;
    }

    private static String hmacSha256Hex(byte[] secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static boolean isGatewayBypassed(WebRequestContext ctx) {
        Boolean flag = ctx.attribute(PlatformProtectionInterceptor.GATEWAY_BYPASS_ATTRIBUTE);
        if (Boolean.TRUE.equals(flag)) {
            return true;
        }
        return ctx.header(PlatformProtectionInterceptor.GATEWAY_HEADER) != null;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 密钥提供 SPI（README.md §4.8.2 实现注：业务方从 Vault / DB / Config 加载）。
     */
    @FunctionalInterface
    public interface SecretKeyProvider {
        byte[] secretFor(String clientKey);
    }

    static String urlEncode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}