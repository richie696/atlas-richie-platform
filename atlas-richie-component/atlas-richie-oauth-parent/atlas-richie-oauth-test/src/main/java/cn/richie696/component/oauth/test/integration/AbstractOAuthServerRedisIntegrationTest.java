package cn.richie696.component.oauth.test.integration;

import cn.richie696.testing.redis.AbstractRedisIntegrationTestBase;
import cn.richie696.testing.redis.RedisIntegrationTestAccess;
import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.cache.GlobalCacheManager;
import cn.richie696.component.cache.local.manage.LocalCache;
import cn.richie696.component.cache.local.manage.LocalCacheManager;
import java.lang.reflect.Field;

import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * OAuth 测试支撑工具（不属于生产运行时）：OAuth Server Redis 集成测试的抽象基类。
 *
 * <p>职责链位置：处于组件通用 Redis 集成测试框架（{@link AbstractRedisIntegrationTestBase}）
 * 与 OAuth Server 的具体集成测试类之间。它强制为所有子类打上
 * {@link OAuthServerRedisIntegrationTest} 标记，使每个用例自动启用
 * {@code it:*} 数据清理、Redis 属性注入与串行执行，
 * 同时把 Redis 连接策略收敛为 {@link OAuthServerRedisIntegrationTestSupport}。</p>
 *
 * <p>解决以下问题：服务工程的 OAuth 集成测试如果各自继承 Redis 通用基类，
 * 会重复声明多个注解并各自管理 {@code it:*} 命名空间，容易遗漏清理；
 * 通过该抽象基类把"标记 + 数据清理 + 串行执行"封装在一起，
 * 子类只需关注 OAuth 协议契约本身。</p>
 *
 * @author richie696
 * @since 2026-08-07
 */
@OAuthServerRedisIntegrationTest
public abstract class AbstractOAuthServerRedisIntegrationTest extends AbstractRedisIntegrationTestBase {

    @Autowired
    private GlobalCacheManager globalCacheManager;

    @Autowired
    private LocalCacheManager localCacheManager;

    @Override
    protected Supplier<RedisIntegrationTestAccess> redisIntegrationTestAccess() {
        return OAuthServerRedisIntegrationTestSupport::getInstance;
    }

    @Override
    protected void onRedisIntegrationTestPrepared() {
        forceStaticDelegate(GlobalCache.class, "DELEGATE", globalCacheManager);
        forceStaticDelegate(LocalCache.class, "MANAGE", localCacheManager);
    }

    private static <T> void forceStaticDelegate(Class<?> holder, String fieldName, T value) {
        try {
            Field field = holder.getDeclaredField(fieldName);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            AtomicReference<T> reference = (AtomicReference<T>) field.get(null);
            reference.set(value);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to wire " + holder.getSimpleName() + " for OAuth integration test", exception);
        }
    }
}
