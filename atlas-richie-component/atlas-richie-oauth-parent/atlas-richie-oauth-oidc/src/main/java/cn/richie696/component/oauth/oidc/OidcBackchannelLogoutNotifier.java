package cn.richie696.component.oauth.oidc;

import java.net.URI;

/**
 * OIDC Backchannel Logout 的 HTTP 投递边界 SPI，把"把 logout_token 推到 RP"这一动作从
 * 编排服务里解耦出去。
 *
 * <p>处于 {@link OidcBackchannelLogoutService} 与 OAuth Service 之间：编排服务只关心
 * "对哪些 RP、推什么 token"，真正的 HTTP 客户端选型、重试策略、熔断降级、日志埋点由实现方
 * （通常是 OAuth Service）在部署侧注入。组件本身不绑定 OkHttp / JDK HttpClient /
 * Spring RestClient 任何一种。
 *
 * <p>解决"组件自带 HTTP 客户端会和 Spring Boot Starter 里的客户端配置冲突"的部署痛点，
 * 让 HTTP 投递风格与项目既有技术栈一致，同时为单测里直接用 lambda 替换实现留出空间。
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface OidcBackchannelLogoutNotifier {

    void notify(URI endpoint, String logoutToken);
}
