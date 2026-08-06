package cn.richie696.component.mcp.protocol.compatibility;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class McpProtocolEraCacheTest {
    @Test
    void expiresNegotiatedVersionByTtl() {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        McpProtocolEraCache cache = new McpProtocolEraCache(Clock.fixed(now, ZoneOffset.UTC));

        cache.put("https://server.example", "2026-07-28", Duration.ofMinutes(1));

        assertThat(cache.get("https://server.example")).isPresent()
                .get().extracting(McpNegotiatedProtocol::version).isEqualTo("2026-07-28");
        assertThat(cache.get("missing")).isEmpty();
    }

    @Test
    void rejectsInvalidCacheInput() {
        McpProtocolEraCache cache = new McpProtocolEraCache();

        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put("server", "unknown", Duration.ofMinutes(1)));
        org.assertj.core.api.Assertions.assertThatIllegalArgumentException()
                .isThrownBy(() -> cache.put("server", "2026-07-28", Duration.ZERO));
    }
}
