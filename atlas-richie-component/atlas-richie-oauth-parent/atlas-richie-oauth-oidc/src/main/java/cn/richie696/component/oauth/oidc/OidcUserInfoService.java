package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC UserInfo 的安全投影服务，按 scope 决定每个 RP 实际能拿到哪些用户 Claims。
 *
 * <p>处于 OAuth Service 的 UserInfo Controller 与 {@link OidcUserInfoProvider} 之间：
 * 上游接 subject 与本次 token 携带的 scopes，下游委托 provider 拿到原始 Claims 视图，
 * 再按 {@code OidcProperties.userInfoScopeClaims} 配置裁剪成"RP 该看到的字段集合"，
 * 最终产出 {@link OidcUserInfo}。
 *
 * <p>解决"OP 把整张用户表里的字段全都吐给 RP、违反最小披露原则"的隐私与合规风险，
 * 把 scope→Claims 的映射收敛到一处配置里，业务侧能按 RP 类型动态调整可见字段，
 * 同时把"subject 为空"或"用户不存在"作为协议级错误抛出而不是返回半成品对象。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OidcUserInfoService {

    private final OidcProperties properties;
    private final OidcUserInfoProvider provider;

    public OidcUserInfoService(OidcProperties properties, OidcUserInfoProvider provider) {
        this.properties = properties;
        this.provider = provider;
    }

    public OidcUserInfo load(String subject, Collection<String> scopes) {
        if (subject == null || subject.isBlank()) {
            throw new BusinessException("invalid_token", "UserInfo 缺少 subject");
        }
        Map<String, Object> source = provider.findClaims(subject);
        if (source == null) {
            throw new BusinessException("invalid_token", "用户不存在或已失效");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, List<String>> scopeClaims = properties.effectiveUserInfoScopeClaims();
        if (scopes != null) {
            scopes.forEach(scope -> {
                for (String claim : scopeClaims.getOrDefault(scope, List.of())) {
                    if (source.containsKey(claim) && source.get(claim) != null) {
                        result.put(claim, source.get(claim));
                    }
                }
            });
        }
        return new OidcUserInfo(subject, result);
    }
}
