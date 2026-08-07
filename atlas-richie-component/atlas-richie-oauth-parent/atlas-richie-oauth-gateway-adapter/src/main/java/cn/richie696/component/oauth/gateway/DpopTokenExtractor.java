package cn.richie696.component.oauth.gateway;

/**
 * RFC 9449 {@code Authorization: DPoP token} 头部纯解析工具。
 *
 * <p>职责链位置：与 {@link BearerTokenExtractor} 并列的协议头部解析器，
 * 处于 HTTP 入口到 {@link OAuthGatewayAdapter} 之间。它只识别 RFC 9449
 * 新引入的 {@code DPoP} scheme 并返回原始 token 字符串，不参与 DPoP proof
 * 签名校验、JWK thumbprint 绑定或 nonce 校验；那些职责归属于
 * {@code oauth-resource-server} 中的 {@code DpopProofValidator}。</p>
 *
 * <p>解决以下问题：当网关同时需要支持传统 Bearer 与 RFC 9449 DPoP 凭证时，
 * 调用方可以按 Bearer → DPoP 顺序尝试解析而不重复实现 RFC 9449 §7.1 语法约束，
 * 解析失败统一返回 {@code null}，由 {@link OAuthGatewayAdapter} 决定拒绝或回退。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class DpopTokenExtractor {

    private DpopTokenExtractor() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"DPoP".equalsIgnoreCase(authorization.substring(0, separator))) {
            return null;
        }
        String token = authorization.substring(separator + 1).trim();
        return token.isBlank() || token.indexOf(' ') >= 0 ? null : token;
    }
}
