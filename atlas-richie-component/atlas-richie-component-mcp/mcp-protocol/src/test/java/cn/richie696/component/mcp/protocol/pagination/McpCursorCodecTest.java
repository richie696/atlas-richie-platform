package cn.richie696.component.mcp.protocol.pagination;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpCursorCodecTest {
    @Test
    void roundTripsOpaqueCursor() {
        McpCursorCodec codec = new McpCursorCodec("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String cursor = codec.encode(42);

        assertThat(cursor).doesNotContain("42");
        assertThat(codec.decode(cursor)).isEqualTo(42);
    }

    @Test
    void rejectsTamperedCursor() {
        McpCursorCodec codec = new McpCursorCodec("0123456789abcdef".getBytes(StandardCharsets.UTF_8));
        String cursor = codec.encode(42);

        assertThatThrownBy(() -> codec.decode(cursor.substring(0, cursor.length() - 1) + "x"))
                .hasMessageContaining("Invalid pagination cursor");
    }
}
