package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.PrecomputedVectorRecord;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;

import java.util.List;

/**
 * 可选的预计算向量数据面。
 *
 * <p>当模型版本由业务对象固定时，调用方先生成向量，再通过本接口写入和检索。该窄接口
 * 不加入 {@link VectorService} 的最小强制契约，未支持原始向量数据面的 Provider 无需实现。</p>
 */
public interface PrecomputedVectorOperations {

    /** 幂等创建一个固定维度和距离度量的物理索引。 */
    void ensureIndex(String indexName, int dimension, String metric);

    /** 按记录 ID 幂等写入一条已生成向量。 */
    void upsertPrecomputed(PrecomputedVectorRecord record);

    /** 在同一索引内批量幂等写入已生成向量。 */
    void upsertAllPrecomputed(String indexName, List<PrecomputedVectorRecord> records);

    /** 删除明确 ID 集合；空集合无操作。 */
    void deletePrecomputed(String indexName, List<String> vectorIds);

    /** 使用调用方提供的查询向量检索，并把结构化过滤下推到 Provider。 */
    List<VectorSearchResult> searchByVector(
            String indexName,
            float[] vector,
            int limit,
            SearchOptions options);
}
