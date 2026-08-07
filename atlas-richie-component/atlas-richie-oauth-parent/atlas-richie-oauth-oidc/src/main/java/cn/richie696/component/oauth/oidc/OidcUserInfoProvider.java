package cn.richie696.component.oauth.oidc;

import java.util.Map;

/** OAuth Service 注入的用户 Claims 查询 SPI，不规定用户数据库或身份模型。 */
@FunctionalInterface
public interface OidcUserInfoProvider {

    Map<String, Object> findClaims(String subject);
}
