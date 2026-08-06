package cn.richie696.component.mcp.schema;

import java.util.List;

public record McpSchemaValidationResult(List<McpSchemaViolation> violations) {
    private static final McpSchemaValidationResult VALID = new McpSchemaValidationResult(List.of());

    public McpSchemaValidationResult {
        violations = List.copyOf(violations);
    }

    public static McpSchemaValidationResult valid() {
        return VALID;
    }

    public boolean isValid() {
        return violations.isEmpty();
    }
}
