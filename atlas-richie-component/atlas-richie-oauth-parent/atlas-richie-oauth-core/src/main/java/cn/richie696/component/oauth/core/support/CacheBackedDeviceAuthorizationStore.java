package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.OAuthLock;
import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;

/** 使用 OAuthCache 的 RFC 8628 状态存储。 */
public final class CacheBackedDeviceAuthorizationStore implements cn.richie696.component.oauth.core.spi.DeviceAuthorizationStore {

    private static final String PREFIX = "oauth:device:";
    private final OAuthCache cache;

    public CacheBackedDeviceAuthorizationStore(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public void save(DeviceAuthorizationRecord record, long ttlMillis) {
        cache.put(deviceKey(record.deviceCode()), record, ttlMillis);
        cache.put(userKey(record.userCode()), record.deviceCode(), ttlMillis);
    }

    @Override
    public DeviceAuthorizationRecord findByDeviceCode(String deviceCode) {
        return deviceCode == null ? null : cache.get(deviceKey(deviceCode), DeviceAuthorizationRecord.class);
    }

    @Override
    public DeviceAuthorizationRecord findByUserCode(String userCode) {
        String deviceCode = userCode == null ? null : cache.get(userKey(userCode), String.class);
        return findByDeviceCode(deviceCode);
    }

    @Override
    public void update(DeviceAuthorizationRecord record, long ttlMillis) {
        save(record, ttlMillis);
    }

    @Override
    public DeviceAuthorizationRecord consumeAuthorized(String deviceCode, String clientId) {
        String key = deviceKey(deviceCode);
        try (OAuthLock lock = cache.tryLock(key + ":consume", 5L)) {
            if (!lock.acquired()) {
                return null;
            }
            DeviceAuthorizationRecord record = findByDeviceCode(deviceCode);
            if (record == null || !record.clientId().equals(clientId)
                    || record.status() != DeviceAuthorizationRecord.Status.AUTHORIZED
                    || record.expiresAt() <= System.currentTimeMillis()) {
                return null;
            }
            cache.remove(key);
            cache.remove(userKey(record.userCode()));
            return record;
        }
    }

    @Override
    public PollSnapshot poll(String deviceCode, String clientId, long now, long intervalMillis) {
        String key = deviceKey(deviceCode);
        try (OAuthLock lock = cache.tryLock(key + ":poll", 5L)) {
            if (!lock.acquired()) {
                return new PollSnapshot(null, true);
            }
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
    }

    private String deviceKey(String value) {
        return PREFIX + "code:" + value;
    }

    private String userKey(String value) {
        return PREFIX + "user:" + value;
    }
}
