package cn.richie696.component.vector.service;

import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorSearchResult;

import java.nio.file.Path;
import java.util.List;

/**
 * 语义检索能力。
 *
 * <p>它是 {@link VectorService} 的四个必选子接口之一，定义所有 provider 都必须支持的
 * 检索入口：纯文本、纯图像（字节 / 路径）。filter、namespace、rerank、minScore 等
 * 调谐参数集中在 {@link SearchOptions} 中传递，避免重载爆炸。</p>
 *
 * <p>设计要点：
 * <ul>
 *   <li>文本路径可能命中 {@code tryRerank}（{@code AbstractVectorService} 提供的可选重排）
 *       ，由 {@link SearchOptions#rerank} 控制；图像路径默认不重排，因为
 *       dual-encoder 图像模型通常已经在对齐空间内</li>
 *   <li>返回的 {@link VectorSearchResult#vector} 在图像检索路径上由本接口实现类
 *       主动填充（用于可能的 MMR），文本路径由 provider 决定 — 不保证非空</li>
 *   <li>provider 不支持的检索形态不会在本接口出现；hybrid、multi-vector 等进阶能力
 *       走独立窄接口</li>
 * </ul>
 *
 * <p>调用关系：
 * <ul>
 *   <li>由 {@link VectorService} 继承暴露给业务层</li>
 *   <li>由 {@code cn.richie696.component.vector.knowledge.DefaultKnowledgeBaseVectorService}
 *       在 dense 路径上调用</li>
 *   <li>由 {@code AbstractVectorService} 提供 rerank、filter 编译等公共逻辑</li>
 * </ul>
 *
 * @author richie696
 * @version 1.0
 * @since 2025-07-01
 */
public interface VectorSearchOperations {

    /**
     * 在指定索引上对纯文本做向量语义检索。
     *
     * <p>执行过程（{@code AbstractVectorService} 默认实现）：
     * <ol>
     *   <li>校验 {@code indexName} 非空、{@code text} 非空白</li>
     *   <li>用 {@link SearchOptions} 中的结构化
     *       {@link cn.richie696.component.vector.model.VectorFilter} 编译 provider filter DSL</li>
     *   <li>调用 provider 检索得到候选</li>
 *   <li>若 {@link SearchOptions#rerank} 不为 {@code false}，调用
 *       {@code tryRerank} 重排</li>
     * </ol>
     *
     * @param indexName 索引/collection 名称，非空
     * @param text      查询文本，非空白字符
     * @param limit     返回条数上限；{@code <= 0} 时默认 {@code 10}
     * @param options   检索调谐选项（filter、rerank、minScore、namespace 等），
     *                  {@code null} 视为默认空选项
     * @return 按相关性降序的 {@link VectorSearchResult} 列表，可能为空但不会为 {@code null}
     * @throws IllegalArgumentException 当 {@code indexName} 或 {@code text} 为空时
     * @throws UnsupportedOperationException 当 filter 同时设了结构化和表达式，
     *                                       或 provider 缺少
     *                                       {@code VectorFilterCompiler} 时
     */
    List<VectorSearchResult> searchByText(String indexName, String text, int limit, SearchOptions options);

    /**
     * 以图像字节流作为查询向量进行检索。
     *
     * <p>要求当前 provider 已配置 image embedding 模型（{@code ModalityAwareEmbeddingService}
     * 支持 {@code IMAGE}）；否则抛
     * {@link cn.richie696.component.vector.exceptions.UnsupportedModalityException}。
     * 图像向量检索通常直接使用图像 embedding 模型对齐后的空间，{@code minScore} 阈值
     * 的选取应与文本检索保持一致口径。</p>
     *
     * @param indexName 索引名称，非空
     * @param image     图像字节流，非空
     * @param mimeType  MIME 类型（如 {@code image/png}），非空
     * @param limit     返回条数上限
     * @param minScore  最低相似度阈值；{@code 0.0} 表示不过滤
     * @return 命中候选；provider 缺失图像模型时抛 {@link cn.richie696.component.vector.exceptions.UnsupportedModalityException}
     */
    List<VectorSearchResult> searchByImage(String indexName, byte[] image, String mimeType, int limit, double minScore);

    /**
     * 以本地图像路径作为查询向量进行检索。
     *
     * <p>内部读取 {@link Path} 字节并复用 {@code searchByImage(byte[])} 实现；
     * 调用方应确保文件存在、可读，且不超过 image 嵌入模型支持的尺寸上限。</p>
     *
     * @param indexName 索引名称，非空
     * @param imagePath 图像文件路径，非空且可读
     * @param mimeType  MIME 类型，非空
     * @param limit     返回条数上限；{@code minScore} 在此重载中固定为 {@code 0.0}
     * @return 命中候选；provider 缺失图像模型时抛 {@link cn.richie696.component.vector.exceptions.UnsupportedModalityException}
     */
    List<VectorSearchResult> searchByImage(String indexName, Path imagePath, String mimeType, int limit);

}
