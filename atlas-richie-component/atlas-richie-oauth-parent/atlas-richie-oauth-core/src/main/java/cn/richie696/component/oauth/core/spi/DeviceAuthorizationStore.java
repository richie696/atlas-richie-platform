package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;

/**
 * RFC 8628 Device Authorization 状态的存储端口。
 * <p>
 * 提供 device_code / user_code 短期生命周期、轮询间隔限速、授权态原子消费这些契约;默认走
 * {@link cn.richie696.component.oauth.core.support.CacheBackedDeviceAuthorizationStore} 走 Redis,
 * OAuth Service 可注入自定义实现,例如把状态推到外部会话中心。
 * </p>
 * <p>
 * 处于 oauth-core 的短期状态接入位置:由 {@link DeviceAuthorizationService} 直接调用;其
 * 原子消费语义是设备码安全的关键,生产实现必须在分布式锁内完成"读 + 删",杜绝并发轮询导致
 * {@code slow_down} 被绕过。
 * </p>
 * <p>
 * 解决的问题:把设备授权流的短期状态与"轮询限速 + 一次性消费"这种安全敏感的并发语义封装成 SPI,
 * 让业务方替换存储后端时不会破坏防重放与速率限制的保证。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface DeviceAuthorizationStore {

    void save(DeviceAuthorizationRecord record, long ttlMillis);

    DeviceAuthorizationRecord findByDeviceCode(String deviceCode);

    DeviceAuthorizationRecord findByUserCode(String userCode);

    void update(DeviceAuthorizationRecord record, long ttlMillis);

    /** 仅当状态为 AUTHORIZED 时原子消费设备码。 */
    DeviceAuthorizationRecord consumeAuthorized(String deviceCode, String clientId);

    /**
     * 读取并记录一次轮询。生产实现应在同一把分布式锁内完成，避免并发客户端绕过轮询间隔。
     */
    default PollSnapshot poll(String deviceCode, String clientId, long now, long intervalMillis) {
        DeviceAuthorizationRecord record = findByDeviceCode(deviceCode);
        if (record == null || !clientId.equals(record.clientId())) {
            return new PollSnapshot(null, false);
        }
        boolean slowDown = record.lastPolledAt() > 0
                && now - record.lastPolledAt() < intervalMillis;
        if (!slowDown) {
            update(new DeviceAuthorizationRecord(record.deviceCode(), record.userCode(), record.clientId(),
                    record.scopes(), record.resource(), record.status(), record.subject(),
                    record.expiresAt(), now), Math.max(1, record.expiresAt() - now));
        }
        return new PollSnapshot(record, slowDown);
    }

    record PollSnapshot(DeviceAuthorizationRecord record, boolean slowDown) {
    }
}
