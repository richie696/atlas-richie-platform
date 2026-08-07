package cn.richie696.component.oauth.oidc;

import java.util.Map;

/**
 * OIDC UserInfo 的 Claims 查询 SPI，由 OAuth Service 在启动时按业务侧身份模型注入。
 *
 * <p>处于 {@link OidcUserInfoService} 与 OAuth Service 的用户表 / IdP 适配层之间：
 * 上游 UserInfo 域对象按 scope 过滤时调用本接口拉取原始 Claims，下游实现方自行决定
 * 是查用户表、调用上游 IdP 还是聚合多个身份源。组件不绑定数据库、SSO、LDAP 或
 * 任何身份协议，让 OIDC 层保持中立。
 *
 * <p>解决"OIDC 组件自带用户表会和各业务系统的 IdP 冲突"的可替换性问题，让同一个
 * oauth-oidc 模块既能跑在零信任 IdP 项目里，也能跑在传统 RBAC 用户表里，
 * 测试时还能用 lambda 直接返回固定 Map。
 *
 * @author richie696
 * @since 2026-08-07
 */
@FunctionalInterface
public interface OidcUserInfoProvider {

    Map<String, Object> findClaims(String subject);
}
