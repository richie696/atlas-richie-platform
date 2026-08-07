package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.contract.model.OAuthIntrospectionResponse;
import cn.richie696.component.oauth.contract.model.OAuthPrincipal;

import java.net.URI;
import java.util.Arrays;

/**
 * 先本地 JWT 校验、失败后按策略 introspection 的 Resource Server Facade。
 *
 * <p>处于 HTTP 过滤器 / Spring Security Filter 与 {@link JwtTokenVerifier} +
 * {@link IntrospectionClient} + {@link DpopProofValidator}（可选）三者之间：上游
 * 传入 Bearer access token 与可选 DPoP proof / 请求 method / URI，下游按"JWT 优先
 * → introspection fallback → DPoP 绑定"的工作流完成鉴权并产出 {@link OAuthPrincipal}。
 * Facade 不感知 HTTP 框架与具体 JWT 库，仅编排三条可插拔路径并发出 Micrometer 指标。
 *
 * <p>解决"Resource Server 必须在每个项目里写一遍 JWT-vs-introspection 选择与 DPoP 绑定
 * 校验"的重复劳动，把可配置的工作流（纯 JWT / 纯 introspection / JWT+fallback /
 * 加 DPoP）收敛到一个 Facade，业务侧只需要装配所需 SPI，不必关心编排细节。
 *
 * @author richie696
 * @since 2026-08-07
 */
public class ResourceServerAuthenticator {

    private final JwtTokenVerifier jwtTokenVerifier;
    private final IntrospectionClient introspectionClient;
    private final boolean introspectionFallback;
    private final OAuthResourceServerMetrics metrics;
    private final DpopProofValidator dpopProofValidator;

    public ResourceServerAuthenticator(JwtTokenVerifier jwtTokenVerifier,
                                       IntrospectionClient introspectionClient,
                                       boolean introspectionFallback) {
        this(jwtTokenVerifier, introspectionClient, introspectionFallback, null);
    }

    public ResourceServerAuthenticator(JwtTokenVerifier jwtTokenVerifier,
                                       IntrospectionClient introspectionClient,
                                       boolean introspectionFallback,
                                       OAuthResourceServerMetrics metrics) {
        this(jwtTokenVerifier, introspectionClient, introspectionFallback, metrics, null);
    }

    public ResourceServerAuthenticator(JwtTokenVerifier jwtTokenVerifier,
                                       IntrospectionClient introspectionClient,
                                       boolean introspectionFallback,
                                       OAuthResourceServerMetrics metrics,
                                       DpopProofValidator dpopProofValidator) {
        this.jwtTokenVerifier = jwtTokenVerifier;
        this.introspectionClient = introspectionClient;
        this.introspectionFallback = introspectionFallback;
        this.metrics = metrics;
        this.dpopProofValidator = dpopProofValidator;
    }

    public OAuthPrincipal authenticate(String accessToken) {
        return authenticate(accessToken, null, null, null);
    }

    /** 使用 DPoP proof 验证请求绑定；未配置 DPoP 时保持原有 Bearer 行为。 */
    public OAuthPrincipal authenticate(String accessToken, String method,
                                       URI requestUri, String dpopProof) {
        OAuthPrincipal principal;
        try {
            if (jwtTokenVerifier == null) {
                throw new ResourceServerException("未配置 JWT 校验器");
            }
            principal = jwtTokenVerifier.verify(accessToken);
        } catch (RuntimeException jwtFailure) {
            if (metrics != null) metrics.authenticationFailed();
            if (!introspectionFallback || introspectionClient == null) {
                throw jwtFailure;
            }
            if (metrics != null) metrics.introspectionFallbackUsed();
            OAuthIntrospectionResponse response = introspectionClient.introspect(accessToken);
            if (response == null || !response.active()) {
                throw new ResourceServerException("access token inactive");
            }
            principal = new OAuthPrincipal(response.subject(), response.clientId(), response.issuer(),
                    response.audience(), response.tokenId(),
                    response.scope() == null || response.scope().isBlank()
                            ? java.util.List.of()
                            : Arrays.stream(response.scope().split("\\s+")).toList(),
                    response.claims());
        }
        if (dpopProof != null && !dpopProof.isBlank()) {
            if (dpopProofValidator == null) {
                throw new ResourceServerException("DPoP 未配置");
            }
            try {
                dpopProofValidator.validate(dpopProof, accessToken, method, requestUri);
            } catch (RuntimeException dpopFailure) {
                if (metrics != null) metrics.authenticationFailed();
                throw dpopFailure;
            }
        }
        if (metrics != null) metrics.authenticationSucceeded();
        return principal;
    }
}
