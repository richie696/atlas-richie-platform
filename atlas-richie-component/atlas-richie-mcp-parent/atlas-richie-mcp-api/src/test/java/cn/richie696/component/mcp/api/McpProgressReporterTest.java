package cn.richie696.component.mcp.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpProgressReporter} NOOP 单例与自定义实现的 progress/total/message 接收语义。
 */
@DisplayName("McpProgressReporter 进度回报端口")
class McpProgressReporterTest {

    @Test
    @DisplayName("NOOP 单例：调用不应抛异常也不应保留状态")
    void noopShouldSilentlySwallow() {
        McpProgressReporter.NOOP.report(0.5, 100.0, "halfway");
        McpProgressReporter.NOOP.report(1.0, null, null);
        // 无外部状态可断言；只要不抛异常即可
        assertThat(McpProgressReporter.NOOP).isNotNull();
    }

    @Test
    @DisplayName("自定义实现：依次收到 progress/total/message")
    void shouldReceiveAllArguments() {
        List<String> captured = new ArrayList<>();
        McpProgressReporter reporter = (progress, total, message) -> {
            captured.add(progress + "|" + total + "|" + message);
        };

        reporter.report(0.25, 4.0, "first quarter");
        reporter.report(0.5, 4.0, "halfway");
        reporter.report(1.0, null, "done");

        assertThat(captured).containsExactly(
                "0.25|4.0|first quarter",
                "0.5|4.0|halfway",
                "1.0|null|done");
    }

    @Test
    @DisplayName("允许 progress 超出 [0,1]：约束由实现自行决定")
    void shouldNotEnforceProgressRange() {
        boolean[] invoked = {false};
        McpProgressReporter reporter = (p, t, m) -> invoked[0] = true;

        reporter.report(1.5, null, null);
        reporter.report(-0.1, null, null);

        assertThat(invoked[0]).isTrue();
    }
}
