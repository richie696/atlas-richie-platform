package cn.richie696.component.mcp.api;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server 与 Client 共用的稳定调用上下文。
 *
 * <p>该 record 承担 MCP 体系中"调用横切信息"的统一载体职责：一次 Tool/Resource/Prompt
 * 调用从入口（HTTP/SSE/Stdio）到业务实现，再回到出口，整条链路共享同一份 {@code McpCallContext}。
 * 把这些信息集中在一处的好处是：业务代码可以无视协议来源，直接读取租户、主体、协议版本、
 * 取消信号、进度回报等横切维度。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>{@code attributes} 在紧凑构造器内做防御性不可变拷贝，避免外部突变污染调用上下文；</li>
 *   <li>{@code cancellationToken} 与 {@code progressReporter} 在缺省时回落到 {@code NONE/NOOP}，
 *       使业务代码无需判空即可调用，符合 JDK 函数式接口的"空对象"惯例；</li>
 *   <li>{@code deadline} 允许为空（无超时场景），通过 {@link #optionalDeadline()} 暴露为 {@code Optional}。</li>
 * </ul>
 *
 * @param requestId 协议层透传的唯一请求 ID，用于链路追踪与日志关联
 * @param protocolVersion 协议版本号，业务代码可基于版本做兼容处理
 * @param tenantId 租户标识，由 {@link cn.richie696.component.mcp.api.server.McpCallContextFactory} 解析得到
 * @param subject 主体标识（用户/服务账号），用于审计与权限判定
 * @param deadline 调用截止时间；为 {@code null} 时表示无超时
 * @param attributes 任意键值对扩展属性（traceId、来源渠道等）
 * @param cancellationToken 协作式取消令牌，缺省回落到 {@link McpCancellationToken#NONE}
 * @param progressReporter 进度回报端口，缺省回落到 {@link McpProgressReporter#NOOP}
 * @author richie696
 * @since 2026-08-11
 */
public record McpCallContext(
        String requestId,
        String protocolVersion,
        String tenantId,
        String subject,
        Instant deadline,
        Map<String, Object> attributes,
        McpCancellationToken cancellationToken,
        McpProgressReporter progressReporter) {

    /**
     * 紧凑构造器：完成不可变包装与缺省值回落。
     *
     * @param requestId 请求 ID
     * @param protocolVersion 协议版本（非空）
     * @param tenantId 租户 ID
     * @param subject 主体标识
     * @param deadline 截止时间，可为 {@code null}
     * @param attributes 扩展属性，会被不可变拷贝
     * @param cancellationToken 取消令牌，缺省回落为 {@link McpCancellationToken#NONE}
     * @param progressReporter 进度回报，缺省回落为 {@link McpProgressReporter#NOOP}
     * @throws NullPointerException 当 {@code protocolVersion} 为 {@code null} 时
     */
    public McpCallContext {
        protocolVersion = Objects.requireNonNull(protocolVersion, "protocolVersion");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        cancellationToken = cancellationToken == null ? McpCancellationToken.NONE : cancellationToken;
        progressReporter = progressReporter == null ? McpProgressReporter.NOOP : progressReporter;
    }

    /**
     * 将 {@link #deadline} 暴露为 {@link Optional}，便于业务代码在不确定是否存在超时的场景下做链式处理。
     *
     * @return 包装后的截止时间；无超时设置时返回 {@link Optional#empty()}
     */
    public Optional<Instant> optionalDeadline() {
        return Optional.ofNullable(deadline);
    }
}
