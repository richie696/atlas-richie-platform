package cn.richie696.component.oauth.cache;

/**
 * OAuth 分布式锁的 AutoCloseable 句柄, 持有 acquired() 与 close() 两个最小方法, 屏蔽底层 Redis 锁实现细节。
 * <p>
 * 处于 OAuth 缓存适配层的 "锁抽象" 一环, 由 {@link OAuthCache#tryLock(String, long)} 返回, 配合 try-with-resources 在 token endpoint / device flow 等需要互斥的关键段使用, 避免调用方直接依赖 CacheLock 内部 API。
 * 解决"OAuth 业务需要分布式互斥 (例如防 device_code 重放、token 重用) 但不想耦合具体缓存实现"的问题, 用单接口暴露锁语义, 便于替换底层实现或叠加埋点。
 *
 * @author richie696
 * @since 2026-08-07
 */
public interface OAuthLock extends AutoCloseable {

    boolean acquired();

    @Override
    void close();
}
