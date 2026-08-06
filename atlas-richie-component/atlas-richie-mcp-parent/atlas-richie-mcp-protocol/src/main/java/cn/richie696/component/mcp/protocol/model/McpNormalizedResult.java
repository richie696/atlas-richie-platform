package cn.richie696.component.mcp.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 归一化结果显式携带完成状态，兼容旧版本缺省语义。
 */
public record McpNormalizedResult(ResultType resultType, Map<String, Object> payload) {
    public McpNormalizedResult {
        resultType = Objects.requireNonNull(resultType, "resultType");
        payload = payload == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
    }

    public enum ResultType {
        COMPLETE,
        INPUT_REQUIRED
    }
}
