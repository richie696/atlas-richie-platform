package cn.richie696.component.oauth.gateway;

/**
 * RFC 6750 {@code Authorization: Bearer} 头部纯解析工具。
 *
 * <p>职责链位置：网关流量入口的最早一层，处于 HTTP 协议层与 {@link OAuthGatewayAdapter} 之间。
 * 它只承担协议头部到原始 token 字符串的解析，不参与签名校验、scope 判定或受众验证；
 * 严格的 RFC 6750 语法白名单（大小写不敏感的 scheme、单一空白分隔、不允许额外空白字符）
 * 在此一次完成，避免上层重复实现与出现宽松/严格不一致的情况。</p>
 *
 * <p>解决以下问题：网关需要在 Servlet / WebFlux 入口处快速剥离 Bearer 凭证，
 * 同时对缺失、空白、多空格、scheme 不匹配等异常输入给出统一的 {@code null} 响应，
 * 把错误处理留给上层决策；该工具不抛出异常，使调用方能够用最朴素的 if-else 流程串联 DPoP 提取器。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class BearerTokenExtractor {

    private BearerTokenExtractor() {
    }

    public static String extract(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"Bearer".equalsIgnoreCase(authorization.substring(0, separator))) {
            return null;
        }
        String token = authorization.substring(separator + 1).trim();
        return token.isBlank() || token.indexOf(' ') >= 0 ? null : token;
    }
}
