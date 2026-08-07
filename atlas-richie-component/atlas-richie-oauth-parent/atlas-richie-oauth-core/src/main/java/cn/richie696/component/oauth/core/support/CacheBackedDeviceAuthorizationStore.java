package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.OAuthCache;
import cn.richie696.component.oauth.cache.OAuthLock;
import cn.richie696.component.oauth.core.model.DeviceAuthorizationRecord;

/**
 * 基于 {@link OAuthCache} 的 RFC 8628 Device Authorization 状态存储默认实现。
 * <p>
 * 同时维护 {@code device_code → 记录} 与 {@code user_code → device_code} 两份映射;消费与轮询
 * 均通过 {@link OAuthLock} 在 Redis 分布式锁内完成"读 + 删 + slow_down 判定",杜绝并发绕过
 * 轮询间隔。Key 命名走 {@code oauth:device:code:/oauth:device:user:},与
 * {@link cn.richie696.component.oauth.core.config.OAuth2RedisKey} 风格一致但本类自行管理前缀。
 * </p>
 * <p>
 * 处于 oauth-core 的默认短期存储实现位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 在缺省 Bean 时注册,被 {@link cn.richie696.component.oauth.core.DeviceAuthorizationService} 持有。
 * </p>
 * <p>
 * 解决的问题:用 Redis 自身能力提供"短期状态 + 原子消费 + 限速"三合一语义,既让 DeviceAuthorizationService
 * 保持无状态,又把"防 slow_down 绕过"这个安全敏感点封装到一个高复用度的类里。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
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
