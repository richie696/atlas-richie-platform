package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;

/**
 * Relying Party 调用 OAuth 协议端点的端口抽象，覆盖 token 申请与 introspection 两条主路径。
 *
 * <p>处于业务系统（Gateway Service / 第三方应用）与外部 Authorization Server 之间：上游
 * 业务侧把 {@link OAuthTokenRequest} 传进来调用 {@link #requestToken}，或在收到 opaque
 * access token 后调用 {@link #introspect} 校验状态；下游实现（默认
 * {@link StandardOAuthTokenClient}）按 RFC 6749 / RFC 7662 把请求序列化为
 * {@code application/x-www-form-urlencoded} 并把响应反序列化为领域模型。接口不绑定
 * HTTP 客户端与缓存策略，方便在测试中替换为 mock、生产中替换为带重试/熔断的包装器。
 *
 * <p>解决"业务侧需要直接跟 AS 通信时必须自选 HTTP 库、自封装 form 序列化、自解析
 * OAuth 错误响应"的重复劳动，把"调用 AS 颁发 token / 校验 token 状态"这两个最常用
 * 入口抽象为可替换端口，让 oauth-client 模块成为业务系统对接任意 OAuth AS 的统一门户。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface OAuthTokenClient {

    OAuthTokenResponse requestToken(OAuthTokenRequest request);

    OAuthIntrospectionResponse introspect(String token);
}
