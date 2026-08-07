package cn.richie696.component.oauth.client;

import java.util.Map;

/** 调用上游 OIDC Provider UserInfo endpoint 的客户端端口。 */
public interface OidcUserInfoClient {

    Map<String, Object> load(String accessToken);
}
