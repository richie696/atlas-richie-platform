package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpException;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolAuditEvent;
import cn.richie696.component.mcp.api.server.McpToolAuditSink;
import cn.richie696.component.mcp.api.server.McpToolInvocation;
import cn.richie696.component.mcp.api.server.McpToolInvocationChain;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Emits sanitized audit events without allowing audit failures to affect Tool execution. */
/**
 * Tool 调用审计拦截器：在 Tool 执行后写入脱敏后的审计事件，绝不影响业务结果。
 *
 * <p>设计要点：
 * <ul>
 *   <li>仅当 Tool 描述符的 {@code audit} 注解为 true 时才采集，避免无差别写入带来的存储压力。</li>
 *   <li>敏感参数通过三条规则识别：
 *       ① {@code sensitiveArguments} 显式列表；
 *       ② inputSchema 中标记 {@code x-mcp-sensitive: true} 的属性；
 *       ③ 参数名匹配常见敏感词（authorization / token / password / secret / credential / access_token）。
 *       匹配命中的值统一替换为 {@code ***}，并在嵌套结构中递归脱敏。</li>
 *   <li>异常隔离：写入失败被吞掉并通过 {@link McpToolAuditSink} 上层感知，
 *       保证审计能力不会反过来破坏 Tool 执行路径。</li>
 * </ul>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpAuditInvocationInterceptor implements McpToolInvocationInterceptor {
    private static final String REDACTED = "***";
    private static final Set<String> SENSITIVE_NAMES = Set.of(
            "authorization", "token", "access_token", "password", "secret", "credential");

    private final McpToolAuditSink sink;

    /**
     * 构造审计拦截器。
     *
     * @param sink 审计事件接收器，不能为 null
     */
    public McpAuditInvocationInterceptor(McpToolAuditSink sink) {
        this.sink = sink;
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE + 200;
    }

    @Override
    public CompletionStage<McpToolResponse> intercept(
            McpToolInvocation invocation,
            McpToolInvocationChain chain) {
        if (!Boolean.TRUE.equals(invocation.tool().annotations().get("audit"))) {
            return chain.proceed(invocation);
        }
        Instant startedAt = Instant.now();
        long startedNanos = System.nanoTime();
        CompletionStage<McpToolResponse> stage = chain.proceed(invocation);
        return stage.whenComplete((response, throwable) -> record(
                invocation, response, throwable, startedAt, startedNanos));
    }

    private void record(
            McpToolInvocation invocation,
            McpToolResponse response,
            Throwable throwable,
            Instant startedAt,
            long startedNanos) {
        try {
            boolean successful = throwable == null && response != null && !response.error();
            sink.record(new McpToolAuditEvent(
                    invocation.tool().name(),
                    invocation.context().requestId(),
                    invocation.context().tenantId(),
                    invocation.context().subject(),
                    startedAt,
                    Duration.ofNanos(Math.max(0, System.nanoTime() - startedNanos)),
                    successful,
                    errorCode(response, throwable),
                    sanitizeArguments(invocation)));
        } catch (RuntimeException ignored) {
            // Audit is observational and must never change the business result.
        }
    }

    private String errorCode(McpToolResponse response, Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof McpException exception) return exception.errorCode();
        if (failure != null) return "MCP_TOOL_INTERNAL_ERROR";
        if (response != null && response.error()
                && response.structuredContent() instanceof Map<?, ?> content
                && content.get("code") instanceof String code) return code;
        return null;
    }

    private Map<String, Object> sanitizeArguments(McpToolInvocation invocation) {
        Set<String> schemaSensitive = sensitiveSchemaProperties(invocation);
        Map<String, Object> result = new LinkedHashMap<>();
        invocation.arguments().forEach((name, value) -> result.put(
                name, schemaSensitive.contains(name) || sensitiveName(name)
                        ? REDACTED : sanitizeValue(value)));
        return result;
    }

    private Set<String> sensitiveSchemaProperties(McpToolInvocation invocation) {
        Object configured = invocation.tool().annotations().get("sensitiveArguments");
        Set<String> result = new java.util.LinkedHashSet<>();
        if (configured instanceof Collection<?> names) {
            for (Object name : names) {
                if (name instanceof String text) result.add(text);
            }
        }
        Object rawProperties = invocation.tool().inputSchema().get("properties");
        if (!(rawProperties instanceof Map<?, ?> properties)) return result;
        properties.forEach((name, schema) -> {
            if (name instanceof String text && schema instanceof Map<?, ?> values
                    && Boolean.TRUE.equals(values.get("x-mcp-sensitive"))) result.add(text);
        });
        return result;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> result = new LinkedHashMap<>();
            map.forEach((key, entry) -> result.put(
                    key, key instanceof String name && sensitiveName(name)
                            ? REDACTED : sanitizeValue(entry)));
            return result;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private boolean sensitiveName(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return SENSITIVE_NAMES.stream().anyMatch(normalized::contains);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException
                && current.getCause() != null) current = current.getCause();
        return current;
    }
}
