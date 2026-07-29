package cn.richie696.component.chunking;

import java.util.Objects;

/**
 * 语义切片的调用级选项；重试与熔断仍归调用编排层负责。
 */
public record SemanticChunkingOptions(SemanticFailureMode failureMode) {
    public SemanticChunkingOptions {
        failureMode = Objects.requireNonNull(failureMode, "failureMode must not be null");
    }

    public static SemanticChunkingOptions defaults () {
        return new SemanticChunkingOptions(SemanticFailureMode.FAIL_FAST);
    }
}
