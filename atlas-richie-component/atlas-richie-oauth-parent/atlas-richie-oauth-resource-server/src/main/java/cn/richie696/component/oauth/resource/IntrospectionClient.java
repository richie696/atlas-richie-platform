package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;

/** Resource Server 调用 AS introspection 的端口。 */
public interface IntrospectionClient {

    OAuthIntrospectionResponse introspect(String accessToken);
}
