package com.richie.component.web.core.protection;

import com.richie.component.web.core.spi.support.DefaultWebInterceptorChain;
import com.richie.component.web.core.spi.support.MutableWebRequestContext;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSignatureInterceptorTest {

    private static final byte[] SECRET = "test-secret-key".getBytes(StandardCharsets.UTF_8);
    private static final String CODE = "SIGNATURE_INVALID";
    private static final String MSG = "请求签名校验失败 (reason={reason})";
    private static final ApiSignatureInterceptor.SecretKeyProvider KEY =
            key -> SECRET;

    private final NonceCache nonceCache = new NonceCache(600);
    private final ApiSignatureInterceptor interceptor =
            new ApiSignatureInterceptor(KEY, nonceCache, 300, 401, CODE, MSG);

    private static String sign(byte[] secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static MutableWebRequestContext.Builder baseCtx() {
        return MutableWebRequestContext.builder()
                .method("POST").path("/api/v1/users");
    }

    @Test
    void validSignature_passesThrough() throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = "nonce-valid-1";
        String canonical = "POST\n/api/v1/users\n" + ts + "\n" + nonce;
        String sig = sign(SECRET, canonical);
        MutableWebRequestContext ctx = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(ts))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .header(ApiSignatureInterceptor.HEADER_SIGNATURE, sig)
                .build();
        ctx.setClientKey("user-1");
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void missingHeaders_deny() throws Exception {
        MutableWebRequestContext ctx = baseCtx().build();
        ctx.setClientKey("user-1");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.responseStatus()).isEqualTo(401);
        assertThat(ctx.shortCircuitBody()).contains("missing_header");
    }

    @Test
    void badTimestamp_deny() throws Exception {
        MutableWebRequestContext ctx = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, "not-a-number")
                .build();
        ctx.setClientKey("user-1");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("bad_timestamp");
    }

    @Test
    void timestampSkew_deny() throws Exception {
        long oldTs = System.currentTimeMillis() - 10 * 60 * 1000L;
        String nonce = "nonce-skew-1";
        MutableWebRequestContext ctx = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(oldTs))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .build();
        ctx.setClientKey("user-1");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("timestamp_skew");
    }

    @Test
    void nonceReplay_deny() throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = "nonce-replay-1";
        String canonical = "POST\n/api/v1/users\n" + ts + "\n" + nonce;
        String sig = sign(SECRET, canonical);

        MutableWebRequestContext first = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(ts))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .header(ApiSignatureInterceptor.HEADER_SIGNATURE, sig)
                .build();
        first.setClientKey("user-1");
        interceptor.intercept(first, new DefaultWebInterceptorChain(List.of()));
        assertThat(first.isShortCircuited()).isFalse();

        MutableWebRequestContext second = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(ts))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .header(ApiSignatureInterceptor.HEADER_SIGNATURE, sig)
                .build();
        second.setClientKey("user-1");
        interceptor.intercept(second, new DefaultWebInterceptorChain(List.of()));
        assertThat(second.isShortCircuited()).isTrue();
        assertThat(second.shortCircuitBody()).contains("nonce_replay");
    }

    @Test
    void signatureMismatch_deny() throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = "nonce-bad-sig";
        MutableWebRequestContext ctx = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(ts))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .header(ApiSignatureInterceptor.HEADER_SIGNATURE, "deadbeef" + "00".repeat(30))
                .build();
        ctx.setClientKey("user-1");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        assertThat(ctx.isShortCircuited()).isTrue();
        assertThat(ctx.shortCircuitBody()).contains("signature_mismatch");
    }

    @Test
    void noClientKey_passesThrough() throws Exception {
        MutableWebRequestContext ctx = baseCtx().build();
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
    }

    @Test
    void gatewayBypass_skipsAllChecks() throws Exception {
        long ts = System.currentTimeMillis();
        String nonce = "nonce-gateway-bypass";
        MutableWebRequestContext ctx = baseCtx()
                .header(ApiSignatureInterceptor.HEADER_TIMESTAMP, String.valueOf(ts))
                .header(ApiSignatureInterceptor.HEADER_NONCE, nonce)
                .header(PlatformProtectionInterceptor.GATEWAY_HEADER, "prod:cluster-a:gw-1")
                .build();
        ctx.setClientKey("user-1");
        boolean[] proceeded = {false};
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of((c, ch) -> proceeded[0] = true)));
        assertThat(proceeded[0]).isTrue();
        assertThat(ctx.isShortCircuited()).isFalse();
    }

    @Test
    void decisionAttribute_isSetOnDenial() throws Exception {
        MutableWebRequestContext ctx = baseCtx().build();
        ctx.setClientKey("user-1");
        interceptor.intercept(ctx, new DefaultWebInterceptorChain(List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = ctx.attribute(ApiSignatureInterceptor.DECISION_ATTRIBUTE);
        assertThat(decision).isNotNull();
        assertThat(decision.get("reason")).isEqualTo("missing_header");
    }

    @Test
    void getOrder_is220() {
        assertThat(interceptor.getOrder()).isEqualTo(220);
    }
}