package cn.richie696.component.oauth.resource;

import cn.richie696.component.oauth.cache.OAuthCache;

/**
 * 基于 {@code oauth-cache} 的分布式 DPoP jti 防重放实现，是生产部署的默认选择。
 *
 * <p>处于 {@link DpopProofValidator} 与 oauth-cache 抽象缓存之间：上游校验器调用
 * {@link #markIfUnseen}，本实现借助 {@code OAuthCache#putIfAbsent} 完成跨实例的
 * 一次性写入与原子性保证，从而让多个 Resource Server 副本共享同一份 jti 视图。
 * 它把所有 Redis Key 命名（{@code oauth:dpop:jti:*}）封装在本类内部，对外只暴露
 * {@link DpopReplayStore} 协议。
 *
 * <p>解决"多实例 / 多节点 Resource Server 部署时 DPoP 抗重放仅靠单机 Map 失效"
 * 的协议降级风险，把 RFC 9449 §5 要求 jti 在合理窗口内全局唯一的约束收敛到一处
 * 实现，业务侧只需要注入分布式 OAuthCache 即可，无需关心 Key 拼写或 TTL 计算。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class OAuthCacheDpopReplayStore implements DpopReplayStore {

    private final OAuthCache cache;

    public OAuthCacheDpopReplayStore(OAuthCache cache) {
        this.cache = cache;
    }

    @Override
    public boolean markIfUnseen(String jti, long ttlMillis) {
        return cache.putIfAbsent("oauth:dpop:jti:" + jti, "1", ttlMillis);
    }
}
