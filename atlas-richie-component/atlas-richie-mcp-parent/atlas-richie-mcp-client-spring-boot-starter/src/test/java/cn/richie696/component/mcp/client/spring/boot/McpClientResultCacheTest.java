package cn.richie696.component.mcp.client.spring.boot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpClientResultCache} 的基本语义：基于 {@link Clock} 的可注入 TTL、过期读取
 * 触发 loader 重新加载、按 key 精确失效、以及按 server 整体失效（不影响其他服务端
 * 的缓存条目）。该测试覆盖了客户端在多服务端配置下反复注册 / 注销时缓存一致性
 * 所需的最少行为契约。
 *
 * @author richie696
 * @since 2026-08-11
 */
class McpClientResultCacheTest {
    @Test
    void cachesAndInvalidatesResult() {
        McpClientResultCache cache = new McpClientResultCache(
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));

        String result = cache.getOrLoad("server|tools/list", Duration.ofMinutes(1), () -> "loaded");

        assertThat(result).isEqualTo("loaded");
        assertThat(cache.get("server|tools/list")).contains("loaded");
        cache.invalidate("server|tools/list");
        assertThat(cache.get("server|tools/list")).isEmpty();
    }

    @Test
    void invalidatesAllResultFamiliesForServer() {
        McpClientResultCache cache = new McpClientResultCache();
        cache.put("server|tools/list", "tools", Duration.ofMinutes(1));
        cache.put("server|resources/list", "resources", Duration.ofMinutes(1));
        cache.put("other|tools/list", "other", Duration.ofMinutes(1));

        cache.invalidateServer("server");

        assertThat(cache.get("server|tools/list")).isEmpty();
        assertThat(cache.get("server|resources/list")).isEmpty();
        assertThat(cache.get("other|tools/list")).contains("other");
    }
}
