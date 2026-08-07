package cn.richie696.component.oauth.client;

import java.util.Map;

/**
 * Relying Party 调用上游 OIDC Provider UserInfo endpoint 的客户端端口。
 *
 * <p>处于 OAuth Service / 业务侧 RP 与外部 OIDC Provider 之间：上游调用方把 access
 * token 传入，下游实现（默认 {@link StandardOidcUserInfoClient}）负责按 RFC 6750 把
 * token 作为 Bearer 头投递并把响应 Claims 直接以 Map 形式返回。本接口刻意返回
 * {@code Map<String, Object>} 而非自定义 record，因为不同 IdP 的 UserInfo schema 差异
 * 较大，固化模型会牺牲灵活性。
 *
 * <p>解决"业务系统对接多家外部 IdP 时 UserInfo 字段结构不一致、强行用 record 抽象会
 * 频繁 break"的接入难题，把 UserInfo 调用抽象为最小可替换端口，让生产中既能用默认
 * JDK HttpClient 实现，也能在测试里直接返回固定 Map，跨 IdP 迁移时只换实现即可。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface OidcUserInfoClient {

    Map<String, Object> load(String accessToken);
}
