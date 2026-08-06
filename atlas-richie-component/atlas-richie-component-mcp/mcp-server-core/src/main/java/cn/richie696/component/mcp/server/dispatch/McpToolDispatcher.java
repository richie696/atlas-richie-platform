package cn.richie696.component.mcp.server.dispatch;

import cn.richie696.component.mcp.api.McpCallContext;
import cn.richie696.component.mcp.api.McpCallCancelledException;
import cn.richie696.component.mcp.api.model.McpToolResponse;
import cn.richie696.component.mcp.api.server.McpToolExecutionException;
import cn.richie696.component.mcp.protocol.McpProtocolException;
import cn.richie696.component.mcp.schema.McpSchemaValidationResult;
import cn.richie696.component.mcp.schema.McpSchemaViolation;
import cn.richie696.component.mcp.server.tool.McpResolvedTool;
import cn.richie696.component.mcp.server.tool.McpToolRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

/**
 * Tool 调用的验证、业务执行和错误边界。
 */
public final class McpToolDispatcher {
    private static final int MAX_REPORTED_VIOLATIONS = 10;

    private final McpToolRegistry registry;

    public McpToolDispatcher(McpToolRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

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

        CompletionStage<McpToolResponse> execution;
        try {
            execution = Objects.requireNonNull(
                    tool.registration().handler().handle(arguments, context),
                    "MCP tool handler returned null CompletionStage");
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

    private void validateOutput(McpResolvedTool tool, McpToolResponse response) {
        if (response.error() || !"complete".equals(response.resultType())
                || tool.optionalOutputSchema().isEmpty()) {
            return;
        }
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
        if (failure instanceof McpCallCancelledException cancelledException) {
            return CompletableFuture.failedFuture(cancelledException);
        }
        if (failure instanceof McpProtocolException protocolException) {
            return CompletableFuture.failedFuture(protocolException);
        }
        return CompletableFuture.failedFuture(internalError(failure));
    }

    private void completeFailure(
            CompletableFuture<McpToolResponse> result,
            Throwable throwable) {
        Throwable failure = unwrap(throwable);
        if (failure instanceof McpToolExecutionException executionException) {
            result.complete(toolExecutionError(executionException));
        } else if (failure instanceof McpCallCancelledException cancelledException) {
            result.completeExceptionally(cancelledException);
        } else if (failure instanceof McpProtocolException protocolException) {
            result.completeExceptionally(protocolException);
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

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
