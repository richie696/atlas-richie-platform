package cn.richie696.component.oauth.resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@code ConcurrentHashMap} 的 DPoP jti 防重放实现，仅适用于单节点或测试环境。
 *
 * <p>处于 {@link DpopProofValidator} 与单机进程内存之间：上游校验器调用
 * {@link #markIfUnseen} 尝试登记 jti，本实现通过 {@code putIfAbsent} 保证首次记录
 * 原子成功，并借助惰性清理逻辑避免过期条目无限堆积。它不依赖任何外部存储或时钟源，
 * 但同时也意味着无法跨进程共享状态。
 *
 * <p>解决"开发/测试场景下没 Redis 也想跑通 DPoP 流程"的最简落地问题，提供一个零依赖
 * 可立即装配的默认实现；生产部署必须替换为分布式 {@link OAuthCacheDpopReplayStore}，
 * 否则多实例之间会出现 jti 重复放行、破坏 RFC 9449 的单次消费语义。
 *
 * @author richie696
 * @since 2026-08-07
 */
public final class InMemoryDpopReplayStore implements DpopReplayStore {

    private final Map<String, Long> entries = new ConcurrentHashMap<>();

    @Override
    public boolean markIfUnseen(String jti, long ttlMillis) {
        long expiresAt = System.currentTimeMillis() + Math.max(1L, ttlMillis);
        purgeExpired();
        return entries.putIfAbsent(jti, expiresAt) == null;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        entries.entrySet().removeIf(entry -> entry.getValue() <= now);
    }
}
