package cn.richie696.component.oauth.resource;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 单进程 DPoP 防重放实现，仅适用于单节点或测试环境。 */
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
