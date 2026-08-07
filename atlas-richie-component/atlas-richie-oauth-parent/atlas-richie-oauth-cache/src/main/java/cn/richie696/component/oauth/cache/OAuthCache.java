package cn.richie696.component.oauth.cache;

/**
 * OAuth 运行时缓存抽象, 不暴露底层 Redis Key 结构。
 * <p>
 * 处于 OAuth 缓存适配层的端口一环, 下游 core / authz / dcr 通过它做 KV / putIfAbsent / 原子计数 / 分布式锁等分布式协调, 不依赖任何具体 Redis API 形态。
 * 解决"多个 OAuth 子模块各自拼 Redis Key 命名、升级或切到不同缓存后端时需要全量回归"的问题, 让缓存语义在 OAuth 业务边界内稳定下来, 实现可以按 Redis / 内存 / 第三方 KV 自由替换。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface OAuthCache {

    <T> T get(String key, Class<T> type);

    void put(String key, Object value, long ttlMillis);

    boolean putIfAbsent(String key, String value, long ttlMillis);

    long increment(String key, long delta, long ttlMillis);

    void remove(String key);

    boolean exists(String key);

    OAuthLock tryLock(String key, long leaseSeconds);
}
