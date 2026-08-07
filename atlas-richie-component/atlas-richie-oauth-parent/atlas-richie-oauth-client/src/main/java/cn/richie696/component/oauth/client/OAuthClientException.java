package cn.richie696.component.oauth.client;

/**
 * OAuth 标准客户端调用失败的统一异常类型，承载 HTTP 状态码与 OAuth {@code error} 字段。
 *
 * <p>处于 {@link OAuthTokenClient} / {@link OidcUserInfoClient} / 两个 metadata resolver 与
 * 业务调用方之间：上游 HTTP 客户端把"连接失败 / 4xx / 5xx / JSON 解析失败 / OAuth 协议
 * error 响应"统一包装成本类型，下游业务侧可以基于 {@code statusCode()} 与 {@code errorCode()}
 * 做精细化处理（重试 / 切换 IdP / 触发回退登录）。无检异常设计让它不会污染业务签名，
 * 但同时保留了根因 cause 链便于排查。
 *
 * <p>解决"业务侧对接 OAuth 时面对 IOException / HttpClientErrorException / 业务自定义
 * 异常等多种错误形态"造成的处理代码支离破碎问题，把 AS 通信失败收敛为单一异常类型，
 * 让 Relying Party 只需按 {@code statusCode / errorCode} 决策，不再关心底层 HTTP 库差异。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class OAuthClientException extends RuntimeException {

    private final int statusCode;
    private final String errorCode;

    public OAuthClientException(String message, int statusCode, String errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public OAuthClientException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = 0;
        this.errorCode = null;
    }

    public int statusCode() {
        return statusCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
