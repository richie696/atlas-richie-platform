package cn.richie696.component.chunking.parser;
import cn.richie696.component.parser.model.ReadSummary;
/**
 * parser→chunk 的完整流事件，保留完成与失败语义。
 * <p>
 * 作为 {@link cn.richie696.component.parser.model.ReadEvent} 流经过
 * {@link ParserChunkingAdapter#adaptEvents(javax.util.concurrent.Flow.Publisher,
 * cn.richie696.component.chunking.model.ChunkingRule)} 转换后的对外契约，承载三类事件：
 * <ul>
 *   <li>{@link Section} —— 已产出的一个切片，按到达顺序逐个下发，可被消费 0 到多次；</li>
 *   <li>{@link Finished} —— 整份文档切片成功完成，附带总览指标；</li>
 *   <li>{@link Failed} —— 整份文档切片失败，下游订阅者收到该事件后必须停止继续消费，订阅终止。</li>
 * </ul>
 *
 * <p>不变性：每次事件都分配独立 record，下游可在任意线程上读取字段而无需额外同步；
 * 本 sealed 接口不允许业务方随意扩展子类，扩展点收敛到 adapter 内部以保证流水线语义封闭。
 */
public sealed interface ChunkingEvent permits ChunkingEvent.Section, ChunkingEvent.Finished, ChunkingEvent.Failed {
 /**
  * 已产出的一个切片事件。每次 {@link cn.richie696.component.parser.model.ReadEvent.Section}
  * 可能在内部触发多个 Chunk，但 adapter 会按 Chunk 粒度单独发一次 {@code Section} 事件，
  * 以便下游按 Chunk 做向量化和落库；同一 Chunk 仅发一次，绝不重放。
  *
  * @param value 由 adapter 组装好的 {@link ChunkedSection}，包含 {@code sourceSpans} 等溯源信息。
  */
 record Section(ChunkedSection value) implements ChunkingEvent { }
 /**
  * 文档流正常结束信号；携带本次文档的摘要信息，便于下游做最终一致性校验或收尾打点。
  * <p>
  * 触发时机：上游 {@link cn.richie696.component.parser.model.ReadEvent.Finished} 到达，
  * 且当前文档所有 Chunk 已通过 {@code onNext} 全部送达下游之后，作为本会话最后一个
  * {@code ChunkingEvent} 发出；之后再无任何事件。
  *
  * @param summary        上游解析组件产出的摘要（来自 {@code ReadSummary}），可能为 {@code null}
  *                       （上游未提供摘要时按"无摘要"处理）。
  * @param totalSections  本次文档解析出的总 section 数，与 {@code ChunkingEvent.Section}
  *                       下发次数无强一致关系（一个 section 可产生多个 Chunk）。
  * @param emittedChunks  本会话累计下发的 Chunk 计数，仅供下游观测，不参与业务决策。
  */
 record Finished(ReadSummary summary, int totalSections, int emittedChunks) implements ChunkingEvent { }
 /**
  * 文档流异常结束信号；触发时机：上游 {@link cn.richie696.component.parser.model.ReadEvent.Failed}
  * 到达，或当前 Chunk 处理过程中抛出未捕获异常。
  * <p>
  * 语义约定：收到该事件后下游订阅者必须视本次会话为已终止：
  * <ul>
  *   <li>不应再调用 {@code Subscription.request(n)} 继续拉取；</li>
  *   <li>已成功下发的 Chunk 可保留使用，但不应再期待后续切片补齐；</li>
  *   <li>adapter 自身在发完 {@code Failed} 后不会再发送 {@code onComplete} 或额外事件。</li>
  * </ul>
  *
  * @param error 触发失败的原始异常对象，可能为 {@code null}（上游标记失败但未携带堆栈时）。
  */
 record Failed(Throwable error) implements ChunkingEvent { }
}
