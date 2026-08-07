package cn.richie696.component.oauth.resource;

/**
 * Resource Server 验证链路上任意环节失败的统一异常类型，覆盖 JWT、introspection、DPoP、
 * JWKS 等所有协议校验路径。
 *
 * <p>处于 {@link JwtTokenVerifier} / {@link IntrospectionClient} / {@link DpopProofValidator}
 * / {@link HttpJwkSource} 与 OAuth Resource Server 入口（HTTP 过滤器 / Spring Security）
 * 之间：上游各协议校验器把任何底层错误（JWT 库异常、HTTP 4xx/5xx、签名不匹配、jti 重放）
 * 包装成本类型抛出，下游被统一的异常处理映射为 401/403 与 RFC 6750 风格的 WWW-Authenticate
 * 响应头。无检异常的定位让业务侧不必显式 catch，但保留 cause 链便于排查。
 *
 * <p>解决"Resource Server 抛出 JWTException / IOException / IllegalStateException 等
 * 多种异常导致全局异常处理写得很丑"的痛点，把所有协议验证错误收敛到单一类型，
 * 便于接入方用一处 ControllerAdvice 完成错误响应映射。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class ResourceServerException extends RuntimeException {

    public ResourceServerException(String message) {
        super(message);
    }

    public ResourceServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
