package cn.richie696.component.mcp.api;

/**
 * 业务代码使用的进度回报端口。
 */
@FunctionalInterface
public interface McpProgressReporter {
    McpProgressReporter NOOP = (progress, total, message) -> {
    };

    void report(double progress, Double total, String message);
}
