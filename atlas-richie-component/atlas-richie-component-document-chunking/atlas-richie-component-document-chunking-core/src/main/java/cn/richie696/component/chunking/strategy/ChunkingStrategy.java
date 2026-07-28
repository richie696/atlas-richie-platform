package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;

/**
 * 单一切片算法的内部契约。
 *
 * <p>所有策略接收同一份 {@link ChunkingRule} 与字符串，并返回同一形态的
 * {@link ChunkingResult}。策略只决定“在哪里切”；长度约束、重叠、坐标和诊断由共享支持类
 * 统一处理，避免算法之间产生不一致的记录语义。</p>
 */
public interface ChunkingStrategy {

    /** 本实现负责的规则类型。 */
    ChunkingRule.Strategy type();

    /** 对非空文本执行切片。输入校验与空文本短路由调用方统一完成。 */
    ChunkingResult chunk(String content, ChunkingRule rule);

    /** 语义切片依赖全文，不能用于增量流式会话。 */
    default boolean supportsStreaming() {
        return true;
    }
}
