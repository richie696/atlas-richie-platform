package cn.richie696.component.oauth.oidc;

/**
 * OIDC 协议层校验失败的统一异常类型，覆盖 ID Token 验签、nonce 比对、audience 校验等场景。
 *
 * <p>处于 {@link OidcIdTokenVerifier} / Discovery 等协议验证器与 OAuth Service 的错误处理
 * 切面之间：上游校验器把任意底层异常（JWT 库抛错、Claim 缺失、签名不匹配）包装成本类型
 * 抛出，下游统一被 OAuth Service 的全局异常处理映射为标准错误响应。它是无检异常的子类，
 * 因此不会被签名污染但需要调用方主动捕获或交由 ControllerAdvice 处理。
 *
 * <p>解决"OIDC 校验失败时上层要面对多种底层异常（SignatureException、Claim 不存在、
 * Date 时间漂移等）"导致的错误处理散落问题，把所有协议层校验错误收敛到单一异常类型，
 * 让 OAuth Service 只需针对这一种异常做协议响应映射。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class OidcVerificationException extends RuntimeException {

    public OidcVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
