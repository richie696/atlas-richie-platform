package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

import java.util.List;
import java.util.Map;

/**
 * Access Token 扩展声明的注入端口。
 * <p>
 * 由 {@link AccessTokenSigner} 在签发 JWT 前回调,允许业务方把 {@code tenant_id}、角色等受信任的
 * 扩展 claim 写入 Token;实现应当从可信的租户/身份上下文读取,不能直接信任 token 请求参数,以免
 * 越权注入。
 * </p>
 * <p>
 * 处于 oauth-core 的扩展点位置:由 {@link cn.richie696.component.oauth.core.TokenEndpoint} 与
 * AuthorizationCodeGrant 在签名阶段调用,业务方按需
 * 注入自定义实现即可,无需改动协议层。
 * </p>
 * <p>
 * 解决的问题:在保留 OAuth 标准 claim(iss/sub/aud/exp/iat/nbf/jti/scope/client_id)不可被覆盖的
 * 前提下,把租户、角色等业务 claim 的写入路径开放出去,避免每个租户系统都改一遍签名器。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface AccessTokenClaimsCustomizer {

    Map<String, Object> customize(String clientId, ClientConfig client,
                                   List<String> scopes, String resource);

    static AccessTokenClaimsCustomizer empty() {
        return (clientId, client, scopes, resource) -> Map.of();
    }
}
