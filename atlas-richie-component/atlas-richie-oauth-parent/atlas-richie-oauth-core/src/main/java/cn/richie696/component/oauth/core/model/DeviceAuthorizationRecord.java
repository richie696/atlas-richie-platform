package cn.richie696.component.oauth.core.model;

import java.util.List;

/** Device Authorization 的短期状态，不包含用户密码或完整 Token。 */
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
