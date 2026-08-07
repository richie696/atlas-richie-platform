package cn.richie696.component.oauth.contract.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RFC 8628 Device Authorization Grant 的响应 record, 含 device_code、user_code、verification_uri、verification_uri_complete、expires_in、interval。
 * <p>
 * 处于契约层设备授权端点的出参一环, 由设备授权服务填充, HTTP 适配层按 RFC 直接序列化返回给无浏览器设备, 客户端按 interval 周期轮询 token endpoint 兑换 token。
 * 解决"无可视化能力的设备缺少交互入口, 必须一次性拿到可读 user_code 与可达 URI"的问题, 同时通过 expires_in 与 interval 把限流策略下放到客户端, 减少服务端主动驱逐成本。
 *
 * @author richie696
 * @since 2026-08-07
 */
public record OAuthDeviceAuthorizationResponse(
        @JsonProperty("device_code") String deviceCode,
        @JsonProperty("user_code") String userCode,
        @JsonProperty("verification_uri") String verificationUri,
        @JsonProperty("verification_uri_complete") String verificationUriComplete,
        @JsonProperty("expires_in") long expiresIn,
        long interval
) {
}
