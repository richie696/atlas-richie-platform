package cn.richie696.component.chunking;

import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;

/**
 * 文本切片原子服务的统一入口：{@code String content + ChunkingRule rule -> ChunkingResult}。
 *
 * <p>本接口是组件对外的唯一稳定契约，同步批式入口；不绑定 Spring、不绑定任何 AI SDK。
 * 流式入口请使用 {@link StreamingChunker}。实现必须满足：同一输入 + 同一规则快照应产出
 * 完全可重现的结果，便于重建索引、定位引用和排障。</p>
 *
 * <p>关于 {@link ChunkingRule.Strategy#SEMANTIC}：只有注册了 {@code SemanticBoundaryAdvisor}
 * 的策略工厂才能处理该规则；没有 advisor 时必须明确失败，不得静默退化。调用方仍可直接使用
 * {@link SemanticChunkingService} 完成显式编排。
 * 这是因为语义切片依赖完整上下文，且需要把每个语义段再次交给本接口做长度与 overlap 收尾，
 * 由专门的协调器而非原子服务来承担更合适。</p>
 */
public interface ChunkingService {

    /**
     * 按实现持有的默认规则切片；该默认规则在自动装配实现中来自
     * {@link cn.richie696.component.chunking.config.ChunkingProperties#defaultChunkingRule()}，在裸 {@code new DefaultChunkingService()}
     * 中为 {@code recursiveDefaults(1600, 160)}。
     *
     * <p>该方法仅是 {@link #chunk(String, ChunkingRule)} 的便捷形式：调用方未指定规则时，
     * 走默认规则；想用业务侧持久化的规则快照仍应显式调用
     * {@link #chunk(String, ChunkingRule)}，确保重切片与审计基于同一份规则元数据。</p>
     *
     * <p>覆盖该 default 方法时，必须保持与 {@link #chunk(String, ChunkingRule)} 的“同输入同结果”
     * 契约：实现可以选择用闭包、用构造函数注入的 {@code defaultRule} 字段，或转发到带规则版本。</p>
     *
     * @param content 待切片字符串；{@code null} 或空白返回空结果
     * @return 与 {@code chunk(content, defaultRule)} 等价的结果
     */
    default ChunkingResult chunk(String content) {
        return chunk(content, ChunkingRule.recursiveDefaults(1_600, 160));
    }

    /**
     * 按调用方显式规则切片；这是组件对外的主入口。
     *
     * <p>对 {@code null} 或空白输入返回空 {@link ChunkingResult}，{@code diagnostics.inputCharacters}
     * 反映原始输入长度（{@code null} 输入为 {@code 0}）。SEMANTIC 策略需要已注册的
     * {@code SemanticBoundaryAdvisor}；缺失时明确抛出不支持异常。</p>
     *
     * @param content 待切片字符串；可为 {@code null}
     * @param rule 切片规则快照；{@code null} 触发 {@link NullPointerException}
     * @return 不变的结果对象；当输入被截断或硬切时 {@code diagnostics.hardTruncated} 为 {@code true}
     * @throws NullPointerException 当 {@code rule} 为 {@code null} 时抛出
     * @throws UnsupportedOperationException 当请求的策略未注册时抛出
     */
    ChunkingResult chunk(String content, ChunkingRule rule);
}
