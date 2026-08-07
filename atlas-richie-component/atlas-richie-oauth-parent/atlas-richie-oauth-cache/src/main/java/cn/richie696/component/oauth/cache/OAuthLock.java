package cn.richie696.component.oauth.cache;

/** OAuth 分布式锁句柄。 */
public interface OAuthLock extends AutoCloseable {

    boolean acquired();

    @Override
    void close();
}
