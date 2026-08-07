package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.ClientConfig;

import java.util.List;
import java.util.Map;

/**
 * Access Token 扩展声明注入端口。
 * <p>实现应从可信的租户/身份上下文读取 tenant_id，不能直接信任 token 请求参数。</p>
 */
@FunctionalInterface
public interface AccessTokenClaimsCustomizer {

    Map<String, Object> customize(String clientId, ClientConfig client,
                                   List<String> scopes, String resource);

    static AccessTokenClaimsCustomizer empty() {
        return (clientId, client, scopes, resource) -> Map.of();
    }
}
