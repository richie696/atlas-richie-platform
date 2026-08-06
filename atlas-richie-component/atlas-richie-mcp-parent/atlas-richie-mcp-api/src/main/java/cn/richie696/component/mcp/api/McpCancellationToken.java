package cn.richie696.component.mcp.api;

/**
 * 与传输实现无关的协作式取消信号。
 */
@FunctionalInterface
public interface McpCancellationToken {
    McpCancellationToken NONE = () -> false;

    boolean isCancellationRequested();

    default void throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw new McpCallCancelledException();
        }
    }
}
