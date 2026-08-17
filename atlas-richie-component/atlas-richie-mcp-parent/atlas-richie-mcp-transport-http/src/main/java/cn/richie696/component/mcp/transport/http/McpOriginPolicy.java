package cn.richie696.component.mcp.transport.http;

/**
 * {@code Origin} 验证策略，决定浏览器发起的跨域 MCP 请求是否被允许。
 *
 * <p>MCP Streamable HTTP 协议在设计上把浏览器视为重要的客户端之一，因此服务端必须根据
 * {@code Origin} 头判定是否接受该请求。
 * <ul>
 *   <li>如果请求是浏览器发起的，{@code Origin} 必定存在且不为空。</li>
 *   <li>如果是非浏览器客户端（命令行、桌面应用、服务端内部调用），{@code Origin} 通常缺失
 *       ——此时不视为非法，是否另行鉴权由更上层的认证拦截器决定。</li>
 * </ul>
 *
 * <p>之所以把它定义成函数式接口而非具体类：Origin 允许策略可能涉及同源比较、白名单子网匹配、
 * 按配置下发的正则等多种实现；抽象成谓词后端点本身不感知策略细节，与 {@link McpHttpHeaders#ORIGIN}
 * 等协议知识保持正交。</p>
 *
 * @author richie696
 * @since 2026-08-11
 */
@FunctionalInterface
public interface McpOriginPolicy {
    /**
     * 判断该 Origin 头值是否被允许。
     *
     * @param origin 浏览器发送的 Origin 头值；永远非 null 但允许为空字符串
     * @return {@code true} 表示通过校验
     */
    boolean isAllowed(String origin);

    /**
     * "任何现有 Origin 都被拒绝"的兜底策略，适用于本地开发时关闭浏览器客户端能力。
     * 注意这并不禁止 Origin 缺失的非浏览器请求——它们仍然能进入端点。
     *
     * @return 一个返回 {@code false} 的固定策略
     */
    static McpOriginPolicy denyAllPresentOrigins() {
        return origin -> false;
    }
}
