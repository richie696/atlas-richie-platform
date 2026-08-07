package cn.richie696.component.oauth.core.spi;

import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;

/** RFC 8628 设备授权状态存储端口。 */
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
