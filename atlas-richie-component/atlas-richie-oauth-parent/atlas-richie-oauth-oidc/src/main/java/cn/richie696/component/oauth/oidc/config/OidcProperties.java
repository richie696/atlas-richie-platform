package cn.richie696.component.oauth.oidc.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OIDC Provider 的协议配置，绑定到 {@code platform.oauth.oidc} 配置前缀。
 *
 * <p>处于 Spring Boot 配置加载层与所有 OIDC 协议服务（ID Token 签发、UserInfo 过滤、
 * Discovery 输出、Logout 编排）之间：上游由 {@code application.yml} / {@code .properties}
 * 装配，下游被 {@link OidcProviderMetadataService}、{@link OidcIdTokenService}、
 * {@link OidcUserInfoService} 等服务按需读取。它只声明"OP 说自己支持什么"，与运行时
 * 用户、MFA、Claims 等业务数据完全解耦。
 *
 * <p>解决"OIDC 各端点行为散落在多个 Controller 的注解里、协议能力难以统一审计"
 * 的合规痛点，把 issuer、支持的 scope/grant/response_type、ID Token 签名算法、
 * UserInfo scope→Claims 默认映射等集中到一处，启动期即可一次性校验"声明的能力是否
 * 与 AS 实现一致"。
 *
 * @author richie696
 * @since 2026-08-07
 */
@Data
@ConfigurationProperties(prefix = "platform.oauth.oidc")
public class OidcProperties {

    private boolean enabled = false;
    private String issuer;
    private String authorizationEndpoint;
    private String tokenEndpoint;
    private String deviceAuthorizationEndpoint;
    private String userInfoEndpoint;
    private String jwksUri;
    private String endSessionEndpoint;
    private boolean frontchannelLogoutSupported = true;
    private boolean frontchannelLogoutSessionSupported = true;
    private boolean backchannelLogoutSupported = true;
    private boolean backchannelLogoutSessionSupported = true;
    private boolean requireNonce = true;
    private long idTokenTtlSeconds = 300;
    private String idTokenSigningAlgorithm = "RS256";
    private List<String> responseTypesSupported = new ArrayList<>(List.of("code"));
    private List<String> responseModesSupported = new ArrayList<>(List.of("query", "form_post"));
    private List<String> grantTypesSupported = new ArrayList<>(List.of(
            "authorization_code", "refresh_token", "urn:ietf:params:oauth:grant-type:device_code"));
    private List<String> subjectTypesSupported = new ArrayList<>(List.of("public"));
    private List<String> scopesSupported = new ArrayList<>(List.of("openid", "profile", "email", "address", "phone"));
    private List<String> claimsSupported = new ArrayList<>(List.of(
            "sub", "name", "family_name", "given_name", "preferred_username", "profile",
            "picture", "email", "email_verified", "address", "phone_number", "phone_number_verified"));
    private List<String> tokenEndpointAuthMethodsSupported = new ArrayList<>(List.of("client_secret_basic", "client_secret_post"));
    private List<String> codeChallengeMethodsSupported = new ArrayList<>(List.of("S256"));
    private List<String> idTokenSigningAlgValuesSupported = new ArrayList<>(List.of("RS256"));

    /** 自定义 Scope 到 UserInfo Claims 的映射，避免 UserInfo 默认泄漏用户字段。 */
    private Map<String, List<String>> userInfoScopeClaims = defaultUserInfoScopeClaims();

    public Map<String, List<String>> effectiveUserInfoScopeClaims() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (userInfoScopeClaims != null) {
            userInfoScopeClaims.forEach((scope, claims) ->
                    result.put(scope, claims == null ? List.of() : List.copyOf(claims)));
        }
        return result;
    }

    private static Map<String, List<String>> defaultUserInfoScopeClaims() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("profile", List.of(
                "name", "family_name", "given_name", "middle_name", "nickname",
                "preferred_username", "profile", "picture", "website", "gender",
                "birthdate", "zoneinfo", "locale", "updated_at"));
        result.put("email", List.of("email", "email_verified"));
        result.put("address", List.of("address"));
        result.put("phone", List.of("phone_number", "phone_number_verified"));
        return result;
    }
}
