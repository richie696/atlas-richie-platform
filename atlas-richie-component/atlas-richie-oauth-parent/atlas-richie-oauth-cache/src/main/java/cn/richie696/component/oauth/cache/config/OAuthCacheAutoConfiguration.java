package cn.richie696.component.oauth.cache.config;

import cn.richie696.component.cache.GlobalCacheManager;
import cn.richie696.component.oauth.cache.GlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/** OAuth 缓存组件自动装配。 */
@AutoConfiguration
public class OAuthCacheAutoConfiguration {

    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnBean(GlobalCacheManager.class)
    @ConditionalOnMissingBean(OAuthCache.class)
    public OAuthCache oauthCache() {
        return new GlobalCacheOAuthCache();
    }

    /** 没有平台缓存基础设施时提供单进程安全默认值，生产集群必须注入分布式实现。 */
    @Bean
    @ConditionalOnMissingBean(OAuthCache.class)
    public OAuthCache inMemoryOAuthCache() {
        return new InMemoryOAuthCache();
    }
}
