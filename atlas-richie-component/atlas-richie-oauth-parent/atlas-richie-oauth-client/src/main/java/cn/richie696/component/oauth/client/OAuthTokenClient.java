package cn.richie696.component.oauth.client;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthTokenRequest;
import cn.richie696.component.oauth.contract.model.OAuthTokenResponse;

public interface OAuthTokenClient {

    OAuthTokenResponse requestToken(OAuthTokenRequest request);

    OAuthIntrospectionResponse introspect(String token);
}
