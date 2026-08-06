package cn.richie696.component.chunking;

/**
 * 语义边界服务不可用时的显式处理方式。
 */
public enum SemanticFailureMode {
    /**
     * 默认策略：立即失败，由知识库编排层统一重试、熔断或告警。
     */
    FAIL_FAST,
    /**
     * 明确允许时，改用确定性 RECURSIVE 规则并在诊断中记录降级。
     */
    FALLBACK_TO_DETERMINISTIC
}
