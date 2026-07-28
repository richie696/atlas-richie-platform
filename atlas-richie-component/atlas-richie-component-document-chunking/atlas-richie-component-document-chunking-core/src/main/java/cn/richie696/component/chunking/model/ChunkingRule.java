package cn.richie696.component.chunking.model;

import java.util.List;

/**
 * 不可变的切片规则快照；调用方负责持久化 {@link #ruleId()} 与 {@link #version()} 以重现历史切片。
 *
 * <p>本类是有意为之的“带类型的参数对象”，把全部影响切片行为的字段封装在一个 record 中，
 * 便于：(a) 在向量库随文档版本保存规则快照，重切片时基于快照而不是再次猜测配置；(b) 在
 * 多租户或多模型环境下按 {@code ruleId} + {@code version} 做 A/B 或灰度；(c) 单元测试里把
 * 规则作为可哈希值传入，无需拼装超大 DTO。</p>
 *
 * <p>字段约束见各 {@code @param} 注释；构造器对所有不变量做集中校验。</p>
 *
 * @param ruleId 规则业务标识；调用方负责全局唯一、跨环境稳定（建议带业务前缀，例如 {@code "kb-v1"}）
 * @param version 同一 {@code ruleId} 下的版本号；任一字段变更都应递增版本，旧版本随文档保留
 * @param strategy 切片策略；不同策略对 {@link #separators()} 的使用方式不同，详见 {@link Strategy}
 * @param maxCharacters 单个切片的硬字符上限；必须 {@code > 0} 且严格大于 {@link #overlapCharacters()}
 * @param overlapCharacters 相邻切片的重叠字符数；{@code 0 <= overlap < maxCharacters}，
 *                         保证每段至少能产出一个非空 chunk
 * @param separators 自定义分隔符列表；仅当 {@link #strategy()} 为 {@code RECURSIVE} / {@code SEMANTIC}
 *                   时生效，其余策略使用组件内置分隔符集；为 {@code null} 时回落到内置默认分隔符集
 */
public record ChunkingRule(String ruleId, String version, Strategy strategy, int maxCharacters,
                           int overlapCharacters, List<String> separators) {
    /**
     * 内置的九种切片策略；与 {@link cn.richie696.component.chunking.ChunkingService} / {@link cn.richie696.component.chunking.SemanticChunkingService}
     * 共同构成完整的能力矩阵，详见设计文档第 5 节。
     */
    public enum Strategy {
        /**
         * 固定字符数切片（S1）：完全按 {@code maxCharacters} 切分，不查任何分隔符；
         * 适合无结构原始文本或作为最后兜底。
         *
         * <p><b>适合：</b>日志、二进制 base64、测试数据、模型窗口彻底未知时的应急方案。</p>
         * <p><b>优点：</b>实现最简单、最稳定、无外部依赖。</p>
         * <p><b>局限：</b>中英文 token 比例不一致，最容易在词中截断语义。</p>
         */
        FIXED,
        /**
         * 递归分隔符切片（S2）：按 {@link ChunkingRule#separators()} 中分隔符优先级逐级降级，
         * 是组件默认策略。
         *
         * <p><b>适合：</b>通用纯文本、OCR 文本、解析质量不稳定的 PDF / DOCX。</p>
         * <p><b>优点：</b>无模型、按自然结构降级、对超长块回退到字符切。</p>
         * <p><b>局限：</b>不理解真正语义，分隔符质量决定结果质量。</p>
         */
        RECURSIVE,
        /**
         * 固定 token 数切片（S3）：依赖外部 {@code TokenCounter} SPI 把字符预算转成模型 token，
         * 不查内置分隔符。
         *
         * <p><b>适合：</b>已有确定 Tokenizer、对模型窗口敏感的 RAG 场景。</p>
         * <p><b>优点：</b>与模型窗口严格对齐，可精确控制成本。</p>
         * <p><b>局限：</b>换模型必须重切；core 仅定义 SPI，Tokenizer 实现由调用方注入。</p>
         */
        TOKEN,
        /**
         * 段落切片（S4）：仅按空行（{@code \n\n}）与单换行（{@code \n}）分隔；
         * 适合排版规范的制度、新闻、说明书。
         *
         * <p><b>适合：</b>自然段为主、段落长度差异不大的结构化文本。</p>
         * <p><b>优点：</b>实现简单、结果易解释，能保持自然段完整。</p>
         * <p><b>局限：</b>段落长度差异大时容易碎片；超长段由本组件内部回退到 {@code RECURSIVE} 行为。</p>
         */
        PARAGRAPH,
        /**
         * 句子切片（S5）：按中英文常用句末标点（{@code 。！？.!? }）切分；
         * 适合 FAQ、问答库、客服记录、会议纪要。
         *
         * <p><b>适合：</b>短答案密集、句长可控的自然语言文本。</p>
         * <p><b>优点：</b>窗口重叠自然，便于滑动召回上下文。</p>
         * <p><b>局限：</b>多语言断句规则不全；不保留文档层级；超长句需配合其他策略。</p>
         */
        SENTENCE,
        /**
         * Markdown / 标题切片（S6）：优先按标题前缀（{@code \n# }、{@code \n## } 等）切分，
         * 适合带章节的 Markdown / RST / 技术手册。
         *
         * <p><b>适合：</b>含清晰标题层级、技术文档、产品手册、制度文件。</p>
         * <p><b>优点：</b>保留输入文本中的章节边界；标题路径由编排层关联。</p>
         * <p><b>局限：</b>标题缺失或标题下内容过长时不能单独完成切片。</p>
         */
        MARKDOWN,
        /**
         * HTML 结构切片（S7）：按常见 HTML 结束标签（{@code </h1>}、{@code </p>}、{@code <br/>} 等）切分。
         *
         * <p><b>适合：</b>网页、Wiki、知识库导出的 HTML 片段。</p>
         * <p><b>优点：</b>在保持结构的前提下得到更自然的边界。</p>
         * <p><b>局限：</b>DOM 清洗属于上游能力，本组件不解析完整 HTML；脚本、样式仍需调用方预处理。</p>
         */
        HTML,
        /**
         * 换页符切片（S8）：按换页（{@code \f}）与空行（{@code \n\n}）切分；
         * 适合 PDF / 扫描件 / PPT / 合同。
         *
         * <p><b>适合：</b>带分页符的扫描件、按页结构组织的报告。</p>
         * <p><b>优点：</b>调用方可按页传入并保留页码引用。</p>
         * <p><b>局限：</b>组件不知道页码；调用方需按页 / 幻灯片分别切片，禁用“每 N 页一个 Chunk”。</p>
         */
        PAGE,
        /**
         * 语义切片（S9）：依赖外部 {@code SemanticBoundaryAdvisor} SPI；
         * 仅 {@link cn.richie696.component.chunking.SemanticChunkingService} 接受该策略，{@code ChunkingService} 会拒绝。
         *
         * <p><b>适合：</b>高价值合同、研究报告、复杂长文、付费知识库。</p>
         * <p><b>优点：</b>理论上可识别主题转换，语义完整度最高。</p>
         * <p><b>局限：</b>成本、时延、模型漂移、不可复现；调用方需自行处理失败回退。</p>
         */
        SEMANTIC
    }

    /**
     * 规范构造器：执行集中不变量校验并把 {@link #separators()} 固化为不可变副本。
     *
     * <p>校验要点：{@code ruleId} / {@code version} 非空；{@code strategy} 非空；
     * {@code maxCharacters > 0}；{@code overlapCharacters >= 0}；{@code overlapCharacters < maxCharacters}
     * —— 后一项保证任何一轮至少能产出一个非空 chunk。{@code separators} 为 {@code null} 时回落
     * 到内置默认分隔符集（双换行、单换行、中文句末、英文句末加空格、单空格）。</p>
     *
     * @throws IllegalArgumentException 任一不变量不满足时立即抛出
     */
    public ChunkingRule {
        if (ruleId == null || ruleId.isBlank() || version == null || version.isBlank()
                || strategy == null || overlapCharacters < 0
                || overlapCharacters >= maxCharacters) throw new IllegalArgumentException("非法切片规则");
        separators = separators == null ? List.of("\n\n", "\n", "。", "！", "？", ". ", " ") : List.copyOf(separators);
    }

    /**
     * 工厂方法：用最常见参数构造 RECURSIVE 默认规则快照。
     *
     * <p>{@code ruleId} 固定为 {@code "default-recursive"}、{@code version} 固定为 {@code "1"}，
     * {@code separators} 传 {@code null} 由构造器回落为内置默认集。常用于未指定规则的快速调用
     * 或单元测试。</p>
     *
     * @param max 单切片字符上限；{@code > 0} 且必须大于 {@code overlap}
     * @param overlap 相邻切片重叠字符数；{@code >= 0}
     * @return 等价于 {@code new ChunkingRule("default-recursive", "1", RECURSIVE, max, overlap, null)}
     * @throws IllegalArgumentException 当参数违反 {@link ChunkingRule} 不变量时抛出
     */
    public static ChunkingRule recursiveDefaults(int max, int overlap) {
        return new ChunkingRule("default-recursive", "1", Strategy.RECURSIVE, max, overlap, null);
    }
}
