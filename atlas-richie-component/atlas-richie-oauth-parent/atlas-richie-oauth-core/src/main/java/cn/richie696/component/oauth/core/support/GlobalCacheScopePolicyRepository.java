package cn.richie696.component.oauth.core.support;

import cn.richie696.component.cache.GlobalCache;
import cn.richie696.component.oauth.core.config.OAuth2RedisKey;
import cn.richie696.component.oauth.core.spi.ScopePolicyRepository;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 基于平台 {@code GlobalCache} 的 {@link ScopePolicyRepository} 适配器。
 * <p>
 * 从 Gateway 既有 Redis 策略数据结构读取:api 索引走 {@code gateway:api:index},接口配置走
 * {@code gateway:api:{code}} Hash,接口所需 scope 走 {@code gateway:api:scopes:{code}} Set。
 * 适配层让 {@link ScopeResolver} 不必关心数据如何落 Redis。
 * </p>
 * <p>
 * 处于 oauth-core 的策略适配位置:由 {@link cn.richie696.component.oauth.core.config.OAuth2AutoConfiguration}
 * 作为 {@link ScopePolicyRepository} 的默认 Bean 注册;OAuth Service 可整体替换为 Nacos / Apollo
 * 适配器,本类只负责"读旧版 Redis 数据"这一兼容职责。
 * </p>
 * <p>
 * 解决的问题:让既有的 Gateway Redis 策略数据无需迁移即可被 oauth 组件消费,降低接入成本;同时把
 * Key 命名收敛在 {@link cn.richie696.component.oauth.core.config.OAuth2RedisKey},避免与 Gateway
 * 业务逻辑出现命名冲突。
 * </p>
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class GlobalCacheScopePolicyRepository implements ScopePolicyRepository {

    @Override
    public Set<String> apiCodes() {
        return GlobalCache.collection().get(OAuth2RedisKey.GATEWAY_API_INDEX.getKey(), String.class);
    }

    @Override
    public Map<String, String> apiConfig(String apiCode) {
        Map<String, String> result = GlobalCache.field().getAll(
                OAuth2RedisKey.GATEWAY_API_CONFIG.getKey(apiCode), String.class);
        return result == null ? Collections.emptyMap() : result;
    }

    @Override
    public Set<String> requiredScopes(String apiCode) {
        Set<String> result = GlobalCache.collection().get(
                OAuth2RedisKey.GATEWAY_API_SCOPES.getKey(apiCode), String.class);
        return result == null ? Set.of() : result;
    }
}
