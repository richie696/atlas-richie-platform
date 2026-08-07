package cn.richie696.component.oauth.cache;

/** OAuth 运行时缓存抽象，不暴露底层 Redis Key 结构。 */
public interface OAuthCache {

    <T> T get(String key, Class<T> type);

    void put(String key, Object value, long ttlMillis);

    boolean putIfAbsent(String key, String value, long ttlMillis);

    long increment(String key, long delta, long ttlMillis);

    void remove(String key);

    boolean exists(String key);

    OAuthLock tryLock(String key, long leaseSeconds);
}
