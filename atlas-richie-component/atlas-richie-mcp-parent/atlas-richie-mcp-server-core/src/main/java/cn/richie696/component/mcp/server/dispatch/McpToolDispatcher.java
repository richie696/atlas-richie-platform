package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCallCancelledException;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpArgumentBindingException;
import cn.richie696.component.mcp.api.server.McpToolExecutionException;
import cn.richie696.component.mcp.api.server.McpToolInvocation;
import cn.richie696.component.mcp.api.server.McpToolInvocationChain;
import cn.richie696.component.mcp.api.server.McpToolInvocationInterceptor;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.schema.McpSchemaValidationResult;
import cn.richie696.component.mcp.schema.McpSchemaViolation;
import cn.richie696.component.mcp.server.tool.McpResolvedTool;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/** Tool invocation validation, governance chain, execution and safe error boundary. */
/**
 * Tool 调度器：负责入参校验、治理链编排、调用执行、出参校验与安全错误边界。
 *
 * <p>作为 MCP 服务端对"tools/call" 请求的中央入口，承担以下职责：
 * <ul>
 *   <li>从 {@link McpToolRegistry} 解析并授权 Tool（无权限直接抛 {@code MCP_UNKNOWN_TOOL}）。</li>
 *   <li>对入参按预编译的 JSON Schema 做校验，失败时返回结构化违规列表（最多上报
 *       {@value #MAX_REPORTED_VIOLATIONS} 条以防响应体爆炸）。</li>
 *   <li>把声明在 annotation 上的 {@code timeoutMs}、{@code policies} 注入到
 *       {@link McpToolInvocation} 中供下游拦截器使用。</li>
 *   <li>按 {@link McpToolInvocationInterceptor#order()} 升序串联拦截器链，
 *       链尾最终调用业务 handler。</li>
 *   <li>对 handler 返回值做二次出参校验（声明了 outputSchema 的情况下），并把
 *       所有业务异常映射为统一的 {@link McpToolResponse} 错误体或受控的
 *       {@link McpProtocolException}，避免意外异常泄漏到协议层。</li>
 *   <li>支持调用方通过 {@link cn.richie696.component.mcp.api.McpCancellationToken}
 *       在任意阶段提前取消。</li>
 * </ul>
 * </p>
 *
 * @author richie696
 * @since 2026-08-11
 */
public final class McpToolDispatcher {
    /**
     * 校验失败时最多上报的违规条数，避免响应体被超大违规列表撑爆。
     */
    private static final int MAX_REPORTED_VIOLATIONS = 10;

    private final McpToolRegistry registry;
    private final List<McpToolInvocationInterceptor> interceptors;

    /**
     * 构造无自定义拦截器的调度器。
     *
     * @param registry 工具注册表
     */
    public McpToolDispatcher(McpToolRegistry registry) {
        this(registry, List.of());
    }

    /**
     * 构造调度器并按 {@link McpToolInvocationInterceptor#order()} 升序排列拦截器。
     *
     * @param registry     工具注册表，不能为 null
     * @param interceptors 拦截器集合（顺序会被自动排序，传入顺序无影响）
     */
    public McpToolDispatcher(
            McpToolRegistry registry,
            List<McpToolInvocationInterceptor> interceptors) {
        this.registry = Objects.requireNonNull(registry, "registry");
        List<McpToolInvocationInterceptor> ordered = new ArrayList<>(interceptors);
        ordered.sort(Comparator.comparingInt(McpToolInvocationInterceptor::order));
        this.interceptors = List.copyOf(ordered);
    }

    /**
     * 执行一次 Tool 调用。
     *
     * @param toolName  目标工具名
     * @param arguments 入参映射，不能为 null
     * @param context   调用上下文，不能为 null
     * @return 异步完成的 Tool 响应：成功是正常响应，失败是结构化错误响应或受控异常
     */
    public CompletionStage<McpToolResponse> dispatch(
            String toolName,
            Map<String, Object> arguments,
            McpCallContext context) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(context, "context");
        context.cancellationToken().throwIfCancellationRequested();
        McpResolvedTool tool = registry.resolveAuthorized(toolName, context);

        McpSchemaValidationResult inputValidation = tool.inputSchema().validate(arguments);
        if (!inputValidation.isValid()) {
            return CompletableFuture.completedFuture(validationError(inputValidation));
        }

        Map<String, Object> annotations = tool.registration().descriptor().annotations();
        McpToolInvocation invocation = new McpToolInvocation(
                tool.registration().descriptor(),
                arguments,
                context,
                timeout(annotations.get("timeoutMs")),
                policies(annotations.get("policies")),
                tool.registration().handler());

        CompletionStage<McpToolResponse> execution;
        try {
            execution = Objects.requireNonNull(
                    new DefaultChain(0).proceed(invocation),
                    "MCP tool invocation chain returned null CompletionStage");
        } catch (Throwable throwable) {
            return failedOrToolError(throwable);
        }

        CompletableFuture<McpToolResponse> result = new CompletableFuture<>();
        execution.whenComplete((response, throwable) -> {
            if (throwable != null) {
                completeFailure(result, throwable);
                return;
            }
            try {
                context.cancellationToken().throwIfCancellationRequested();
                McpToolResponse nonNullResponse =
                        Objects.requireNonNull(response, "MCP tool handler returned null response");
                validateOutput(tool, nonNullResponse);
                result.complete(nonNullResponse);
            } catch (Throwable failure) {
                completeFailure(result, failure);
            }
        });
        return result;
    }

    /**
     * 拦截器责任链的递归实现：到达链尾时调用业务 handler，否则把控制权交给当前拦截器。
     */
    private final class DefaultChain implements McpToolInvocationChain {
        private final int index;

        private DefaultChain(int index) {
            this.index = index;
        }

        @Override
        public CompletionStage<McpToolResponse> proceed(McpToolInvocation invocation) {
            // 中文说明：每进入下一级拦截器前先检查取消，避免被取消的请求继续做无意义的工作
            invocation.context().cancellationToken().throwIfCancellationRequested();
            if (index == interceptors.size()) {
                return invocation.handler().handle(invocation.arguments(), invocation.context());
            }
            return interceptors.get(index).intercept(invocation, new DefaultChain(index + 1));
        }
    }

    private void validateOutput(McpResolvedTool tool, McpToolResponse response) {
        if (response.error() || !"complete".equals(response.resultType())
                || tool.optionalOutputSchema().isEmpty()) return;
        McpSchemaValidationResult validation =
                tool.optionalOutputSchema().orElseThrow().validate(response.structuredContent());
        if (!validation.isValid()) {
            throw new McpProtocolException(
                    "MCP_INVALID_TOOL_OUTPUT",
                    -32603,
                    "Tool output did not conform to its declared outputSchema",
                    Map.of(
                            "tool", tool.registration().descriptor().name(),
                            "violationCount", validation.violations().size()));
        }
    }

    private CompletionStage<McpToolResponse> failedOrToolError(Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof McpToolExecutionException executionException) {
            return CompletableFuture.completedFuture(toolExecutionError(executionException));
        }
        if (failure instanceof McpArgumentBindingException bindingException) {
            return CompletableFuture.completedFuture(bindingError(bindingException));
        }
        if (failure instanceof McpCallCancelledException
                || failure instanceof McpProtocolException) {
            return CompletableFuture.failedFuture(failure);
        }
        return CompletableFuture.failedFuture(internalError(failure));
    }

    private void completeFailure(CompletableFuture<McpToolResponse> result, Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof McpToolExecutionException executionException) {
            result.complete(toolExecutionError(executionException));
        } else if (failure instanceof McpArgumentBindingException bindingException) {
            result.complete(bindingError(bindingException));
        } else if (failure instanceof McpCallCancelledException
                || failure instanceof McpProtocolException) {
            result.completeExceptionally(failure);
        } else {
            result.completeExceptionally(internalError(failure));
        }
    }

    private McpToolResponse validationError(McpSchemaValidationResult validation) {
        List<Map<String, Object>> violations = validation.violations().stream()
                .limit(MAX_REPORTED_VIOLATIONS)
                .map(this::violation)
                .toList();
        return new McpToolResponse(
                List.of(Map.of(
                        "type", "text",
                        "text", "Tool arguments did not satisfy the declared input schema.")),
                Map.of(
                        "code", "MCP_TOOL_INPUT_VALIDATION_FAILED",
                        "violations", violations,
                        "truncated", validation.violations().size() > MAX_REPORTED_VIOLATIONS),
                true);
    }

    private McpToolResponse bindingError(McpArgumentBindingException exception) {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", exception.getMessage())),
                Map.of(
                        "code", exception.errorCode(),
                        "argument", exception.argumentName()),
                true);
    }

    private Map<String, Object> violation(McpSchemaViolation violation) {
        return Map.of(
                "instanceLocation", violation.instanceLocation(),
                "keyword", violation.keyword(),
                "message", violation.message());
    }

    private McpToolResponse toolExecutionError(McpToolExecutionException exception) {
        return new McpToolResponse(
                List.of(Map.of("type", "text", "text", exception.getMessage())),
                exception.structuredContent(),
                true);
    }

    private McpProtocolException internalError(Throwable cause) {
        return new McpProtocolException(
                "MCP_TOOL_INTERNAL_ERROR",
                -32603,
                "Tool execution failed",
                Map.of(),
                cause);
    }

    private Duration timeout(Object value) {
        if (!(value instanceof Number number) || number.longValue() <= 0) return null;
        return Duration.ofMillis(number.longValue());
    }

    private Map<String, Object> policies(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        raw.forEach((key, entry) -> {
            if (key instanceof String name) result.put(name, entry);
        });
        return result;
    }

    private Throwable unwrap(Throwable throwable) {
        // 中文说明：CompletionException / ExecutionException 仅是异步包装，需要一直解包到真正的业务根因
        Throwable current = throwable;
        while ((current instanceof CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
