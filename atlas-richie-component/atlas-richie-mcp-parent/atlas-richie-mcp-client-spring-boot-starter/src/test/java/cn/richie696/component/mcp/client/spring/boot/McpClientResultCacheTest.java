package cn.richie696.component.mcp.client.spring.boot;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

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
