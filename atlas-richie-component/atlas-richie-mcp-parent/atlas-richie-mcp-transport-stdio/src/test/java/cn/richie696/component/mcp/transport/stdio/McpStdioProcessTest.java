/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You can obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.richie696.component.mcp.transport.stdio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpStdioProcess} 的子进程生命周期适配器：
 * 启动参数合法性校验、{@code /bin/cat} 作为真实子进程的 round-trip、
 * transport 暴露的 codec 默认值、以及 close() 优雅回收语义。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpStdioProcess 子进程生命周期")
class McpStdioProcessTest {

    private static final List<String> CAT_COMMAND =
            List.of("/bin/cat");

    @Test
    @DisplayName("command 为空或含空白参数抛 IllegalArgumentException")
    void invalidCommandRejected() {
        assertThatThrownBy(() -> McpStdioProcess.start(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("STDIO command");
        assertThatThrownBy(() -> McpStdioProcess.start(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpStdioProcess.start(List.of("/bin/cat", "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("shutdownTimeout 为 null/零/负值抛 IllegalArgumentException")
    void shutdownTimeoutMustBePositive() {
        assertThatThrownBy(() -> McpStdioProcess.start(CAT_COMMAND, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("shutdownTimeout");
        assertThatThrownBy(() -> McpStdioProcess.start(CAT_COMMAND, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> McpStdioProcess.start(CAT_COMMAND, Duration.ofMillis(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("通过 /bin/cat 子进程做 round-trip：send 一行，receive 回显同一行")
    void roundTripsThroughCatProcess() throws Exception {
        try (McpStdioProcess process = McpStdioProcess.start(
                CAT_COMMAND, Duration.ofSeconds(2))) {
            process.transport().send(Map.of("jsonrpc", "2.0", "id", 1));

            Map<String, Object> echoed = process.transport().receive()
                    .orElseThrow(() -> new AssertionError("expected echoed frame"));
            assertThat(echoed).containsEntry("jsonrpc", "2.0").containsEntry("id", 1);
        }
    }

    @Test
    @DisplayName("process() 暴露底层 Process，transport() 暴露 framing 通道")
    void accessorsExposeUnderlyingHandles() throws Exception {
        try (McpStdioProcess process = McpStdioProcess.start(
                CAT_COMMAND, Duration.ofSeconds(2))) {
            assertThat(process.process()).isNotNull();
            assertThat(process.process().isAlive()).isTrue();
            assertThat(process.transport()).isNotNull();
        }
    }

    @Test
    @DisplayName("transport 默认 codec 为 newline-delimited JSON")
    void defaultCodecIsNewlineDelimited() throws Exception {
        try (McpStdioProcess process = McpStdioProcess.start(
                CAT_COMMAND, Duration.ofSeconds(2))) {
            process.transport().send(Map.of("id", 42));
            Map<String, Object> echoed = process.transport().receive().orElseThrow();
            assertThat(echoed).containsEntry("id", 42);
        }
    }

    @Test
    @DisplayName("注入 legacy codec 后子进程使用 Content-Length framing")
    void customCodecPropagatesToTransport() throws Exception {
        try (McpStdioProcess process = McpStdioProcess.start(
                CAT_COMMAND, Duration.ofSeconds(2), new McpLegacyContentLengthCodec())) {
            process.transport().send(Map.of("id", "99"));
            Map<String, Object> echoed = process.transport().receive().orElseThrow();
            assertThat(echoed).containsEntry("id", "99");
        }
    }

    @Test
    @DisplayName("close() 幂等：第二次调用不会抛 IOException")
    void closeIsIdempotent() throws Exception {
        McpStdioProcess process = McpStdioProcess.start(CAT_COMMAND, Duration.ofSeconds(2));
        process.close();
        assertThatCode(process::close).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("不存在的命令路径抛 IOException（启动失败）")
    void startNonExistentCommandThrows() {
        assertThatThrownBy(() -> McpStdioProcess.start(
                List.of("/path/that/does/not/exist"), Duration.ofSeconds(2)))
                .isInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("shutdown timeout 内子进程未退出时，close 强杀并回收")
    void closeDestroyForciblyWhenProcessHangs() throws Exception {
        // 使用 sleep 1000s 的子进程模拟长时间挂起
        List<String> hangingCommand;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            hangingCommand = List.of("cmd", "/c", "ping", "-n", "999", "127.0.0.1");
        } else {
            hangingCommand = List.of("/bin/sleep", "1000");
        }

        McpStdioProcess process = McpStdioProcess.start(
                hangingCommand, Duration.ofMillis(100));
        long start = System.currentTimeMillis();
        process.close();
        long elapsed = System.currentTimeMillis() - start;

        assertThat(process.process().isAlive()).isFalse();
        // destroyForcibly 后通常远小于 1000s；断言 elapsed < 5s 防止挂死
        assertThat(elapsed).isLessThan(5_000L);
    }
}
