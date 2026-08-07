package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;

/**
 * Resource Server 本地校验 JWT 访问令牌的端口抽象。
 *
 * <p>处于 {@link ResourceServerAuthenticator}（JWT 优先路径）与具体 JWT 库实现之间：
 * 上游 Authenticator 把 access token 直接交给本接口，下游任意实现（默认
 * {@link DefaultJwtTokenVerifier}）负责签名校验、issuer / audience 比对、Claims 抽取，
 * 最终产出业务侧统一的 {@link OAuthPrincipal}。接口不暴露 JWT 库差异，方便未来引入
 * EC、JWKS 远程拉取或自定义 Claims 投影。
 *
 * <p>解决"Resource Server 验签逻辑与具体 JWT 库耦合、不利于替换或测试"的实现锁定
 * 问题，把"本地 JWT 校验"这个最小语义抽象出来，让 Authenticator 只关心"校验还是
 * introspection"的策略选择，不被底层库细节绑架。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface JwtTokenVerifier {

    OAuthPrincipal verify(String accessToken);
}
