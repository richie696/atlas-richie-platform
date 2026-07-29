package cn.richie696.component.chunking.strategy;

import cn.richie696.component.chunking.model.ChunkingRule;

/**
 * 供流式会话向同一策略注册表查询增量能力的桥接契约。
 */
public interface StreamingStrategyResolver {
    StreamingChunkingStrategy streamingStrategy(ChunkingRule rule);
}
