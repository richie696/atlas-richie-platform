package cn.richie696.component.oauth.core.support;

import cn.richie696.component.oauth.cache.LegacyGlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;

/**
 * 旧版 {@link cn.richie696.component.oauth.core.spi.TokenStore} 入口,仅保留无参与单参构造 API。
 * <p>
 * 直接继承 {@link CacheBackedTokenStore},行为完全等同;新增代码应直接使用 CacheBackedTokenStore,
 * 本类只用于老版本 YAML/Bean 装配路径的兼容。
 * </p>
 * <p>
 * 处于 oauth-core 的兼容入口位置:不再被自动装配链使用,仅在用户保留旧 Bean 名时兜底;后续版本
 * 将随 {@code @Deprecated(forRemoval=true)} 一起移除。
 * </p>
 * <p>
 * 解决的问题:让老版本服务无需修改 Bean 类型即可升级到新版 oauth 组件,避免因改名导致 Spring 上下文
 * 装配断裂;同时通过继承而非重复实现,保证行为同步。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 * @deprecated 使用 {@link CacheBackedTokenStore},该类只保留旧版本构造 API。
 */
@Deprecated(forRemoval = false)
public class DefaultTokenStore extends CacheBackedTokenStore {

    public DefaultTokenStore() {
        super(new LegacyGlobalCacheOAuthCache());
    }

    public DefaultTokenStore(OAuthCache cache) {
        super(cache);
    }
}
