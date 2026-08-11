package cn.richie696.component.mcp.api.server;

/**
 * Explicitly exposes a safe, named handler to configuration-backed tools.
 *
 * <p>当 Tool 来自部署期 YAML（而非编译期 {@code @McpTool} 注解）时，需要一个明确的
 * "名字 → 处理器"映射以便配置按名引用。{@code McpToolHandlerProvider} 即为该映射的
 * SPI：业务侧通过实现该接口声明可被配置层引用的处理器；YAML 写入 {@code handlerRef:
 * "some-ref"} 后框架会按名查找并绑定到具体 {@link McpToolHandler}。</p>
 *
 * <p>"Safe" 一词的含义：YAML 只能引用已注册的 Provider，避免任意类被反射调用
 * 带来安全风险。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public interface McpToolHandlerProvider {
    /**
     * 处理器引用名（唯一），供 YAML 配置层按名引用。
     *
     * @return 唯一引用名
     */
    String handlerRef();

    /**
     * 返回实际的 {@link McpToolHandler}。
     *
     * @return 非空处理器实例
     */
    McpToolHandler handler();
}
