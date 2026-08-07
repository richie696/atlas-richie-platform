package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthPrincipal;

/** JWT access token 校验端口。 */
public interface JwtTokenVerifier {

    OAuthPrincipal verify(String accessToken);
}
