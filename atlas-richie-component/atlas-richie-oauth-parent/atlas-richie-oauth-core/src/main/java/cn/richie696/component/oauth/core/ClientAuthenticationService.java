package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.core.model.ClientAuthenticationRequest;
import cn.richie696.component.oauth.core.model.ClientAuthenticationResult;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.contract.OAuthErrorCodes;

import java.util.Locale;

/**
 * OAuth 客户端认证领域服务。
 * <p>
 * HTTP Basic、表单参数和 mTLS 证书的解析不属于这里；适配层只需构造
 * {@link ClientAuthenticationRequest}，以后新增认证方式不会扩散到 Gateway 或 AS Controller。
 * </p>
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
