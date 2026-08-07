package cn.richie696.component.oauth.cache.config;

import cn.richie696.component.cache.GlobalCacheManager;
import cn.richie696.component.oauth.cache.GlobalCacheOAuthCache;
import cn.richie696.component.oauth.cache.InMemoryOAuthCache;
import cn.richie696.component.oauth.cache.OAuthCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * OAuth 缓存组件的 Spring Boot 自动装配, 根据平台 {@code GlobalCacheManager} 是否存在选择分布式或单进程默认实现。
 * <p>
 * 处于 OAuth 缓存适配层的 "装配入口" 一环, 在 oauth-cache 依赖被引入时被自动触发, 让下游 core / authz / dcr 通过 {@link OAuthCache} 注入即可使用, 无需各自写 Configuration 类。
 * 解决"OAuth 缓存抽象已定义, 但每个使用方还要重复写一次装配代码"的问题; 同时通过 {@code ConditionalOnMissingBean} 让调用方能轻松替换为自研实现 (例如换缓存后端或加监控包装), 在缺少平台缓存时退化为安全的内存实现。
 *
 * @author richie696
 * @since 2026-08-07
 */
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
