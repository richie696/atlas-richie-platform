package cn.richie696.component.mcp.schema;

import java.util.List;

public final class McpSchemaDefinitionException extends IllegalArgumentException {
    private final List<McpSchemaViolation> violations;

    public McpSchemaDefinitionException(String message) {
        this(message, List.of(), null);
    }

    public McpSchemaDefinitionException(
            String message,
            List<McpSchemaViolation> violations,
            Throwable cause) {
        super(message, cause);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<McpSchemaViolation> violations() {
        return violations;
    }
}
