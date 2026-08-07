package cn.richie696.component.oauth.core.model;

import java.util.List;

/**
 * Device Authorization 的短期状态值对象。
 * <p>
 * 携带 device_code / user_code / 授权态(PENDING/AUTHORIZED/DENIED)/ 过期时间 / 最后轮询时间等
 * 字段;刻意不包含用户密码、refresh token 或完整 access token 等敏感数据,既避免日志泄露,也方便
 * 在审计中脱敏。
 * </p>
 * <p>
 * 处于 oauth-core 的设备码状态位置:由 {@link DeviceAuthorizationService} 在 issue/approve/deny/
 * poll 路径写入与读取,由 {@link DeviceAuthorizationStore} 持久化;反向被
 * {@link cn.richie696.component.oauth.core.TokenEndpoint#exchangeDeviceCode} 消费。
 * </p>
 * <p>
 * 解决的问题:用统一 record 描述"设备码生命周期状态",让 DeviceAuthorizationService 与各种存储实现
 * 共用同一数据契约;同时强制 record 不承载敏感字段,把安全边界前置到模型层。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public record DeviceAuthorizationRecord(
        String deviceCode,
        String userCode,
        String clientId,
        List<String> scopes,
        String resource,
        Status status,
        String subject,
        long expiresAt,
        long lastPolledAt
) {
    public DeviceAuthorizationRecord {
        scopes = scopes == null ? List.of() : List.copyOf(scopes);
        status = status == null ? Status.PENDING : status;
    }

    public enum Status { PENDING, AUTHORIZED, DENIED }
}
