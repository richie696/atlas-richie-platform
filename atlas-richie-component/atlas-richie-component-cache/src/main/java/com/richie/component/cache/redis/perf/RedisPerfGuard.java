package com.richie.component.cache.redis.perf;

import com.richie.component.cache.redis.config.base.AtlasRedisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Redis 调用性能守卫：非 O(1) 告警、慢查询分级、策略性阻断（与 {@link RedisComplexityTier} 对齐）。
 * <p>支持 {@link RedisCommandMeta} 以输出 BIGKEY 探测建议（HLEN/LLEN/SCARD 等）。
 * <p>支持 {@link RedisStringPayloadInspector} 与 {@link #checkStringWritePayload} 检测 String 值滥用（集合/JavaBean 整包序列化、超大 value 等）。
 *
 * @author richie696
 * @version 5.0.0
 * @since 2026-04-03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisPerfGuard {

    private final AtlasRedisProperties redisProperties;

    /**
     * 仅按复杂度分级执行（无元数据探测提示）。
     */
    public <T> T execute(String manager, String method, RedisComplexityTier tier, Supplier<T> supplier) {
        var perf = redisProperties.getPerf();
        if (!perf.isEnabled()) {
            return supplier.get();
        }
        return executeInternal(manager, method, tier, supplier);
    }

    /**
     * 使用 {@link RedisCommandMeta}：在 WARN 非 O(1) 时附带 BIGKEY 探测建议（可配置）。
     */
    public <T> T execute(String manager, String method, RedisCommandMeta meta, Supplier<T> supplier) {
        var perf = redisProperties.getPerf();
        if (!perf.isEnabled()) {
            return supplier.get();
        }
        if (perf.isLogBigKeyProbeHints()) {
            logBigKeyHints(manager, method, meta);
        }
        return executeInternal(manager, method, meta.tier(), supplier);
    }

    /**
     * void 版本。
     */
    public void execute(String manager, String method, RedisComplexityTier tier, Runnable runnable) {
        execute(manager, method, tier, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * void 版本（带元数据）。
     */
    public void execute(String manager, String method, RedisCommandMeta meta, Runnable runnable) {
        execute(manager, method, meta, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * 写入 Redis String 前检测典型滥用（集合/Map/数组、JavaBean 整包、超大文本、疑似 JSON 整包等）。
     * <p>受 {@code spring.data.redis.perf.enabled} 与 {@code warnStringPayloadAntiPatterns} 控制。
     */
    public void checkStringWritePayload(String manager, String method, String key, Object value) {
        var perf = redisProperties.getPerf();
        if (!perf.isEnabled() || !perf.isWarnStringPayloadAntiPatterns()) {
            return;
        }
        RedisStringPayloadInspector.Inspection r = RedisStringPayloadInspector.inspect(value, perf);
        if (r.severity() == RedisStringPayloadInspector.Severity.OK) {
            return;
        }
        String keyHint = ellipsizeKey(key);
        String msg = "[RedisPerf] String value anti-pattern manager=%s method=%s key=%s detail=%s"
                .formatted(manager, method, keyHint, r.detail());
        switch (r.severity()) {
            case WARN -> log.warn(msg);
            case ERROR -> {
                log.error(msg);
                if (perf.isBlockStringPayloadViolations()) {
                    throw new IllegalStateException(msg);
                }
            }
            default -> {
            }
        }
    }

    private static String ellipsizeKey(String key) {
        if (key == null) {
            return "null";
        }
        int max = 160;
        if (key.length() <= max) {
            return key;
        }
        return key.substring(0, max) + "...(" + key.length() + " chars)";
    }

    private void logBigKeyHints(String manager, String method, RedisCommandMeta meta) {
        if (meta.tier() == RedisComplexityTier.O1) {
            return;
        }
        StringBuilder hints = new StringBuilder();
        if (meta.suggestStrlenProbe()) {
            hints.append("STRLEN/MEMORY_USAGE;");
        }
        if (meta.suggestHlenProbe()) {
            hints.append("HLEN;");
        }
        if (meta.suggestLlenProbe()) {
            hints.append("LLEN;");
        }
        if (meta.suggestScardProbe()) {
            hints.append("SCARD;");
        }
        if (meta.suggestZcardProbe()) {
            hints.append("ZCARD;");
        }
        if (!hints.isEmpty()) {
            log.warn("[RedisPerf] BIGKEY probe hints manager={} method={} tier={} suggest=[{}] meta={}",
                    manager, method, meta.tier(), hints, meta.description());
        }
    }

    private <T> T executeInternal(String manager, String method, RedisComplexityTier tier, Supplier<T> supplier) {
        var perf = redisProperties.getPerf();
        long t0 = System.nanoTime();

        if (!isTierAllowedByWhitelist(perf, tier)) {
            String msg = String.format(
                    "[RedisPerf] tier not in tocAllowedComplexities manager=%s method=%s tier=%s allowed=%s",
                    manager, method, tier, perf.getTocAllowedComplexities());
            log.error(msg);
            if (perf.isBlockForbiddenTiers()) {
                throw new IllegalStateException(msg);
            }
        }

        if (perf.isWarnNonO1() && tier != RedisComplexityTier.O1) {
            log.warn("[RedisPerf] non-O(1) call manager={} method={} tier={}", manager, method, tier);
        }

        if (perf.isBlockForbiddenTiers() && shouldBlock(tier)) {
            String msg = "[RedisPerf] blocked by policy manager=" + manager + " method=" + method + " tier=" + tier;
            log.error(msg);
            throw new IllegalStateException(msg);
        }

        try {
            return supplier.get();
        } finally {
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            if (ms >= perf.getTocHardMs()) {
                log.error("[RedisPerf] latency hard threshold manager={} method={} tier={} {}ms (>={}ms) — check BIGKEY / hot key / network",
                        manager, method, tier, ms, perf.getTocHardMs());
            } else if (ms >= perf.getTocSoftMs()) {
                log.warn("[RedisPerf] latency soft threshold manager={} method={} tier={} {}ms (>={}ms) — consider pagination / smaller value / cache aside",
                        manager, method, tier, ms, perf.getTocSoftMs());
            }
        }
    }

    private boolean shouldBlock(RedisComplexityTier tier) {
        return switch (tier) {
            case LINEAR_N, WORSE -> true;
            case LOG_N -> false;
            case O1, SCRIPT_OR_UNKNOWN -> false;
        };
    }

    private boolean isTierAllowedByWhitelist(AtlasRedisProperties.RedisPerf perf, RedisComplexityTier tier) {
        var allow = perf.getTocAllowedComplexities();
        if (allow == null || allow.isEmpty()) {
            return true;
        }
        return allow.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .anyMatch(t -> t.equalsIgnoreCase(tier.name()));
    }
}
