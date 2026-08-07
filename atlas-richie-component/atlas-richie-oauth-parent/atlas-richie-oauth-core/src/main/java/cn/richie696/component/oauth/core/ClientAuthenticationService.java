package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.core.model.ClientAuthenticationRequest;
import cn.richie696.component.oauth.core.model.ClientAuthenticationResult;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.contract.OAuthErrorCodes;

import java.util.Locale;

/**
 * OAuth 客户端认证领域服务。
 * <p>
 * HTTP Basic、表单参数和 mTLS 证书的解析不属于这里;适配层只需构造
 * {@link ClientAuthenticationRequest},以后新增认证方式不会扩散到 Gateway 或 AS Controller。
 * </p>
 * <p>
 * 处于 oauth-core 的客户端认证位置:由 TokenEndpoint、AuthorizationCodeGrant 等需要客户端认证的
 * 协议入口调用;对内委托 {@link ClientRegistry} 做 Secret 校验与客户端元数据查询,失败原因通过
 * {@link ClientAuthenticationResult#errorCode()} 回传给协议层。
 * </p>
 * <p>
 * 解决的问题:把"如何认证一个客户端"从 HTTP 适配层剥离,新增 mTLS、private_key_jwt 等认证方式
 * 只需在适配层补全解析,协议层保持不动;同时把 {@code none}/{@code client_secret_basic}/
 * {@code client_secret_post} 三种基础认证方式集中到一处,避免在多个端点重复校验。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class ClientAuthenticationService {

    private final ClientRegistry clientRegistry;

    public ClientAuthenticationService(ClientRegistry clientRegistry) {
        this.clientRegistry = clientRegistry;
    }

    public ClientAuthenticationResult authenticate(ClientAuthenticationRequest request) {
        if (request == null || request.clientId() == null || request.clientId().isBlank()) {
            return ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
        }

        ClientConfig client = clientRegistry.getClient(request.clientId());
        if (client == null || !Boolean.TRUE.equals(client.getEnabled())) {
            return ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
        }

        String configuredMethod = client.getTokenEndpointAuthMethod();
        String requestedMethod = request.method();
        if (configuredMethod != null && !configuredMethod.isBlank()
                && requestedMethod != null && !requestedMethod.isBlank()
                && !configuredMethod.equalsIgnoreCase(requestedMethod)) {
            return ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
        }

        String method = normalize(requestedMethod, configuredMethod);
        if ("none".equals(method)) {
            return blank(request.clientSecret())
                    ? ClientAuthenticationResult.success(client)
                    : ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
        }
        if (!"client_secret_basic".equals(method) && !"client_secret_post".equals(method)) {
            return ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
        }
        return clientRegistry.verifyClientSecret(request.clientId(), request.clientSecret())
                ? ClientAuthenticationResult.success(client)
                : ClientAuthenticationResult.failure(OAuthErrorCodes.INVALID_CLIENT);
    }

    /** 兼容旧的领域 API：没有公开客户端配置时仍按 client_secret_post 处理。 */
    public ClientAuthenticationResult authenticate(String clientId, String clientSecret) {
        return authenticate(ClientAuthenticationRequest.clientSecretPost(clientId, clientSecret));
    }

    private String normalize(String requested, String configured) {
        String value = requested == null || requested.isBlank() ? configured : requested;
        return value == null || value.isBlank()
                ? "client_secret_post" : value.toLowerCase(Locale.ROOT);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
