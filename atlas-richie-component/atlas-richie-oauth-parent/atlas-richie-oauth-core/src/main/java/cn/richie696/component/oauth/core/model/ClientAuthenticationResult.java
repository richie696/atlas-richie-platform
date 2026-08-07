package cn.richie696.component.oauth.core.model;

/**
 * 客户端认证结果的三元组值对象。
 * <p>
 * 携带 {@code authenticated}、认证成功时的 {@link ClientConfig}、失败时的协议错误码;协议层根据
 * {@code authenticated} 直接分流,失败时通过 {@code errorCode} 映射为 RFC 6749 标准错误响应。
 * </p>
 * <p>
 * 处于 oauth-core 的协议输出位置:由 {@link cn.richie696.component.oauth.core.ClientAuthenticationService} 产出,被 TokenEndpoint、
 * AuthorizationCodeGrant 等协议服务消费;同时为审计、限流等横切关注点提供失败原因。
 * </p>
 * <p>
 * 解决的问题:把"是否认证成功、是哪一类失败"封装为统一返回值,避免在协议层各处重复写 if/else 分流;
 * 同时让认证失败的具体原因(errorCode)可被审计与日志观测。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public record ClientAuthenticationResult(boolean authenticated, ClientConfig client, String errorCode) {

    public static ClientAuthenticationResult success(ClientConfig client) {
        return new ClientAuthenticationResult(true, client, null);
    }

    public static ClientAuthenticationResult failure(String errorCode) {
        return new ClientAuthenticationResult(false, null, errorCode);
    }
}
