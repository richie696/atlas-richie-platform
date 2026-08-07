package cn.richie696.component.oauth.oidc;

import cn.richie696.component.oauth.oidc.config.OidcProperties;
import cn.richie696.contract.exception.BusinessException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 按 OIDC scope 过滤 UserInfo Claims，避免把用户表字段整体暴露给客户端。 */
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
