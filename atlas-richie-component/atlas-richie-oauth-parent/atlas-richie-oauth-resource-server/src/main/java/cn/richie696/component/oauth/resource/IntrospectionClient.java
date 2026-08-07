package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;

/**
 * Resource Server 调 AS introspection endpoint 的端口抽象。
 *
 * <p>处于 {@link ResourceServerAuthenticator}（JWT 失败后的 introspection 路径）与
 * 真正发起 HTTP 请求的实现之间：上游在 JWT 校验失败且开启了 fallback 时调用本接口，
 * 下游实现负责把 access token 投递到 AS 并把响应反序列化为
 * {@link OAuthIntrospectionResponse}。接口不规定 HTTP 客户端、不规定认证方式、
 * 不规定缓存策略，所有这些关注点交给具体实现（包括装饰器式的 {@link CachingIntrospectionClient}）。
 *
 * <p>解决"Resource Server 必须在每个项目里重新实现一遍 introspection HTTP 客户端"
 * 的重复劳动，把 introspection 调用抽象为可替换端口，便于在生产中接入
 * Standard 实现、自研实现或带重试/熔断的包装器。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface IntrospectionClient {

    OAuthIntrospectionResponse introspect(String accessToken);
}
