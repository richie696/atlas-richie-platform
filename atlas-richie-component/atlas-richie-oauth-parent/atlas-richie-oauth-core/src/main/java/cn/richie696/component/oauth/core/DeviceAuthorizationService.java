package cn.richie696.component.oauth.core;

import cn.richie696.component.oauth.contract.OAuthErrorCodes;
import cn.richie696.component.oauth.contract.OAuthGrantTypes;
import cn.richie696.component.oauth.contract.model.OAuthDeviceAuthorizationRequest;
import cn.richie696.component.oauth.contract.model.OAuthDeviceAuthorizationResponse;
import cn.richie696.component.oauth.core.model.ClientConfig;
import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;
import cn.richie696.component.oauth.core.spi.DeviceAuthorizationStore;
import cn.richie696.contract.exception.BusinessException;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * RFC 8628 Device Authorization Grant 的领域服务。
 * <p>
 * 负责设备码全生命周期:签发 {@code device_code}/{@code user_code}、记录授权状态(PENDING/AUTHORIZED/DENIED)、
 * 按轮询间隔返回 {@code authorization_pending}/{@code slow_down}、原子消费已授权设备码。
 * 登录、MFA、用户确认页面由 OAuth Service 注入到 {@code approve}/{@code deny} 入口。
 * </p>
 * <p>
 * 处于 oauth-core 的协议服务位置:依赖 {@link ClientRegistry} 校验客户端与 scope,依赖
 * {@link DeviceAuthorizationStore} 持久化短期状态;被 {@link TokenEndpoint#exchangeDeviceCode}
 * 复用,完成 device_code → access_token 的最终兑换。
 * </p>
 * <p>
 * 解决的问题:把"电视/IoT 等无浏览器设备"的 OAuth 流程封装为可单测的纯领域服务,避免登录 UI 与
 * 设备码状态机耦合;同时把"用户确认后才生成 token"的安全边界显式化,留给 OAuth Service 强制
 * 走一次人类授权。
 * </p>
 *
 * @author richie696
 * @since 2026-06-12
 */
public final class DeviceAuthorizationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final ClientRegistry clientRegistry;
    private final DeviceAuthorizationStore store;
    private final String verificationUri;
    private final long expiresInSeconds;
    private final long pollingIntervalSeconds;

    public DeviceAuthorizationService(ClientRegistry clientRegistry, DeviceAuthorizationStore store,
                                      String verificationUri, long expiresInSeconds,
                                      long pollingIntervalSeconds) {
        this.clientRegistry = clientRegistry;
        this.store = store;
        this.verificationUri = verificationUri;
        this.expiresInSeconds = expiresInSeconds <= 0 ? 600 : expiresInSeconds;
        this.pollingIntervalSeconds = pollingIntervalSeconds <= 0 ? 5 : pollingIntervalSeconds;
    }

    public OAuthDeviceAuthorizationResponse issue(OAuthDeviceAuthorizationRequest request) {
        if (request == null || request.clientId() == null || request.clientId().isBlank()) {
            throw error(OAuthErrorCodes.INVALID_REQUEST, "client_id 必填");
        }
        ClientConfig client = clientRegistry.getClient(request == null ? null : request.clientId());
        if (client == null || !Boolean.TRUE.equals(client.getEnabled())) {
            throw error(OAuthErrorCodes.INVALID_CLIENT, "客户端不存在或已禁用");
        }
        if (client.getGrantTypes() != null && !client.getGrantTypes().isEmpty()
                && !client.getGrantTypes().contains(OAuthGrantTypes.DEVICE_CODE)) {
            throw error("unauthorized_client", "客户端未授权 Device Authorization Grant");
        }
        List<String> scopes = request.scopes().isEmpty() ? client.getScopes() : request.scopes();
        if (scopes == null) scopes = List.of();
        if (client.getScopes() != null && scopes.stream().anyMatch(scope -> !client.getScopes().contains(scope))) {
            throw error("invalid_scope", "请求的 scope 未授权给客户端");
        }
        long expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000;
        String deviceCode = random(48);
        String userCode = readableUserCode();
        DeviceAuthorizationRecord record = new DeviceAuthorizationRecord(deviceCode, userCode,
                client.getClientId(), scopes, request.resource(), DeviceAuthorizationRecord.Status.PENDING,
                null, expiresAt, 0);
        store.save(record, expiresInSeconds * 1000);
        String complete = verificationUri == null ? null : verificationUri + "?user_code=" + userCode;
        return new OAuthDeviceAuthorizationResponse(deviceCode, userCode, verificationUri,
                complete, expiresInSeconds, pollingIntervalSeconds);
    }

    public void approve(String userCode, String subject) {
        DeviceAuthorizationRecord record = findActiveByUserCode(userCode);
        if (subject == null || subject.isBlank()) throw error(OAuthErrorCodes.ACCESS_DENIED, "缺少已认证用户");
        store.update(new DeviceAuthorizationRecord(record.deviceCode(), record.userCode(), record.clientId(),
                record.scopes(), record.resource(), DeviceAuthorizationRecord.Status.AUTHORIZED,
                subject, record.expiresAt(), record.lastPolledAt()), remaining(record));
    }

    public void deny(String userCode) {
        DeviceAuthorizationRecord record = findActiveByUserCode(userCode);
        store.update(new DeviceAuthorizationRecord(record.deviceCode(), record.userCode(), record.clientId(),
                record.scopes(), record.resource(), DeviceAuthorizationRecord.Status.DENIED,
                null, record.expiresAt(), record.lastPolledAt()), remaining(record));
    }

    public DeviceAuthorizationRecord consumeAuthorized(String deviceCode, String clientId) {
        return store.consumeAuthorized(deviceCode, clientId);
    }

    public PollResult poll(String deviceCode, String clientId) {
        DeviceAuthorizationStore.PollSnapshot snapshot = store.poll(deviceCode, clientId,
                System.currentTimeMillis(), pollingIntervalSeconds * 1000L);
        DeviceAuthorizationRecord record = snapshot.record();
        if (record == null || !clientId.equals(record.clientId()) || record.expiresAt() <= System.currentTimeMillis()) {
            return new PollResult(null, "expired_token");
        }
        if (snapshot.slowDown()) {
            return new PollResult(null, OAuthErrorCodes.SLOW_DOWN);
        }
        return switch (record.status()) {
            case PENDING -> new PollResult(null, OAuthErrorCodes.AUTHORIZATION_PENDING);
            case DENIED -> new PollResult(null, OAuthErrorCodes.ACCESS_DENIED);
            case AUTHORIZED -> new PollResult(record, null);
        };
    }

    private DeviceAuthorizationRecord findActiveByUserCode(String userCode) {
        DeviceAuthorizationRecord record = store.findByUserCode(userCode);
        if (record == null || record.expiresAt() <= System.currentTimeMillis()) {
            throw error("expired_token", "user_code 已过期");
        }
        if (record.status() != DeviceAuthorizationRecord.Status.PENDING) {
            throw error(OAuthErrorCodes.ACCESS_DENIED, "设备授权已处理");
        }
        return record;
    }

    private long remaining(DeviceAuthorizationRecord record) {
        return Math.max(1, record.expiresAt() - System.currentTimeMillis());
    }

    private String random(int length) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(length));
    }

    private byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private String readableUserCode() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder result = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            result.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return result.substring(0, 4) + "-" + result.substring(4);
    }

    private BusinessException error(String code, String message) {
        return new BusinessException(code, message);
    }

    public record PollResult(DeviceAuthorizationRecord record, String errorCode) {
        public boolean authorized() { return record != null && errorCode == null; }
    }
}
