package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.VectorRecord;

/**
 * 写入向量记录的核心能力。
 *
 * <p>它是 {@link VectorService} 的四个必选子接口之一，承担"知识库写入"的最小集合：
 * 幂等的 upsert。更新语义通过"按 ID 删除 + 新增"实现，因此本接口不接受
 * {@code partial update} 参数；业务方需要先查询再覆盖。</p>
 *
 * <p>关键不变量：
 * <ul>
 *   <li><b>幂等</b>：相同 {@code vectorId} 重复 upsert 的最终状态等价于单次写入，
 *       不会创建多条记录</li>
 *   <li><b>ID 生成</b>：未提供 {@code vectorId} 时由服务生成 UUID 并回填；
 *       返回值即实际写入的主键，业务方应保存用于后续 delete/update</li>
     *   <li><b>模态路由</b>：根据 {@link VectorRecord#content} 自动选择文本 / 图像
     *       嵌入模型，由 {@code AbstractVectorService.embedRecord} 统一编排</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link VectorService} 继承暴露给业务层</li>
 *   <li>由 {@code AbstractVectorService.upsert} 提供模态路由、ID 生成、
 *       {@code store-managed embedding} 委托等公共逻辑</li>
 *   <li>由业务层（文档导入、知识库写入、单元测试）调用</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorRecordWriteOperations {

    /**
     * 以稳定 vectorId 幂等写入；未提供 ID 时由服务生成。
     *
     * <p>执行过程（{@code AbstractVectorService} 默认实现）：
     * <ol>
     *   <li>校验 {@link VectorRecord} 与 {@code indexName} 非空；{@code content} 非空</li>
     *   <li>{@code id} 为 {@code null} 时生成 UUID 并回填</li>
     *   <li>根据 {@link cn.richie696.component.vector.model.Modality} 选择文本/图像
     *       嵌入模型（无图像模型时抛
     *       {@link cn.richie696.component.vector.exceptions.UnsupportedModalityException}）</li>
     *   <li>调用 provider {@code addEmbeddings} 写入</li>
     * </ol>
     *
     * @param record 待写入的记录；必须包含 {@code indexName} 和 {@code content}
     * @return 实际写入的 {@code vectorId}（若调用方传了 {@code id} 则原样返回，否则为新生成的 UUID）
     * @throws IllegalArgumentException                     {@code record} / {@code indexName} /
     *                                                       {@code content} 为空时
     * @throws cn.richie696.component.vector.exceptions.UnsupportedModalityException                 写入图像内容但 provider 未配置
     *                                                       image 嵌入模型时
     * @throws IllegalStateException                        嵌入模型未配置时
     */
    String upsert(VectorRecord record);

}
