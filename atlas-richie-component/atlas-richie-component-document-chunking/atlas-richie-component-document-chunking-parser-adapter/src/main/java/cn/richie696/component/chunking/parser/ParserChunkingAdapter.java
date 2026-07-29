package cn.richie696.component.chunking.parser;

import cn.richie696.component.chunking.ChunkingService;
import cn.richie696.component.chunking.StreamingChunker;
import cn.richie696.component.chunking.model.Chunk;
import cn.richie696.component.chunking.model.ChunkingResult;
import cn.richie696.component.chunking.model.ChunkingRule;
import cn.richie696.component.parser.model.ParsedSection;
import cn.richie696.component.parser.model.ReadEvent;
import cn.richie696.component.parser.model.ReadResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Flow;

/**
 * parser 公开模型到切片模型的可选适配器；不依赖 parser 内部 SPI。
 * <p>
 * 上游仅消费 {@code atlas-richie-component-document-parser} 暴露的公开契约：
 * 批式入口消费 {@link ReadResult}，流式入口消费
 * {@link Flow.Publisher}{@code <ReadEvent>}。下游产出则固定为切片模型：
 * 批式返回 {@link ChunkedSection} 列表，流式返回
 * {@link Flow.Publisher}{@code <ChunkingEvent>}。
 *
 * <p>与 vector 组件的契约边界：本适配器只把"已切片"的 Chunk 还原成可溯源的
 * {@link ChunkedSection}，不组装 {@code VectorRecord}；把 {@code ChunkedSection}
 * 进一步组装成向量写入请求由 {@code atlas-richie-component-vector-chunk-adapter} 承担，
 * 这样 chunk-adapter 与 vector 写入链路可以独立演进。
 *
 * <p>不变性：实例本身无状态（除构造期就固化的 {@code ChunkingService} 与
 * {@code maxPendingCharacters}），流式会话内部的状态全部封装在每次
 * {@code subscribe} 时新建的 {@link ChunkingSubscriber} 内，不同文档订阅之间
 * 互不污染；同一文档失败后再次调用不会继承任何残留字段。
 *
 * @author richie696
 * @since 2026-07-27
 */
public final class ParserChunkingAdapter {

    private final ChunkingService chunkingService;
    private final int maxPendingCharacters;

    /**
     * 使用默认 pending 缓冲（{@code 8192} 字符，与单参构造器同值）构造适配器。
     *
     * @param chunkingService 底层 {@link ChunkingService}，必须非空。
     */
    public ParserChunkingAdapter(ChunkingService chunkingService) {
        this(chunkingService, 8_192);
    }

    /**
     * 自定义 pending 缓冲上限的构造器；{@code maxPendingCharacters} 会作为
     * {@link StreamingChunker} 的初始 pending 上界传入，实际生效值为
     * {@code max(maxPendingCharacters, rule.maxCharacters())}。
     *
     * @param chunkingService      底层 {@link ChunkingService}，必须非空。
     * @param maxPendingCharacters pending 缓冲上限（字符数），必须大于 0。
     * @throws NullPointerException     {@code chunkingService} 为 {@code null}。
     * @throws IllegalArgumentException {@code maxPendingCharacters <= 0}。
     */
    public ParserChunkingAdapter(ChunkingService chunkingService, int maxPendingCharacters) {
        this.chunkingService = Objects.requireNonNull(chunkingService, "chunkingService must not be null");
        if (maxPendingCharacters <= 0) {
            throw new IllegalArgumentException("maxPendingCharacters 必须大于 0");
        }
        this.maxPendingCharacters = maxPendingCharacters;
    }

    /**
     * 批式适配：每个 parser section 独立切片。
     * <p>
     * 一次性消费整个 {@link ReadResult}：按顺序遍历 {@code result.sections()}，
     * 对每个 {@link ParsedSection} 单独调用 {@link ChunkingService#chunk}；
     * 切片之间互不影响、彼此不拼接，跨 section 不会触发跨段合并逻辑。
     * <p>
     * 因属批式入口，返回的 {@link ChunkedSection} 不携带文件名（{@code fileName} 固定为
     * {@code null}，表示批式维度上的"未知源"），溯源信息以单 span 的形式覆盖整段文本，
     * 与 {@link ChunkedSection#ChunkedSection(int, String, ParsedSection, ChunkingResult)}
     * 便利构造器等价。
     *
     * @param result 上游解析组件的一次性结果，必须非空。
     * @param rule   切片规则，必须非空。
     * @return 与 {@code result.sections()} 一一对应的不可变 {@link ChunkedSection} 列表。
     * @throws NullPointerException 任一参数为 {@code null}。
     */
    public List<ChunkedSection> chunk(ReadResult result, ChunkingRule rule) {
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        List<ChunkedSection> output = new ArrayList<>();
        for (int index = 0; index < result.sections().size(); index++) {
            ParsedSection section = result.sections().get(index);
            output.add(new ChunkedSection(index, null, section, chunkingService.chunk(section.text(), rule)));
        }
        return List.copyOf(output);
    }

    /**
     * 只消费 Chunk 事件的背压 Publisher。
     * <p>
     * 本入口委托给 {@link #adaptEvents(Flow.Publisher, ChunkingRule)}，再在结果之上
     * 套一层 {@link FilteringSubscriber}：仅放行 {@link ChunkingEvent.Section}，
     * 屏蔽 {@link ChunkingEvent.Finished} 与 {@link ChunkingEvent.Failed}。
     * <p>
     * 背压语义：上游 {@link Flow.Subscription} 永不下传给下游，
     * 本 Publisher 自行按下游 {@code request(n)} 的节奏逐元素拉取上游事件；
     * 当下游尚未消费完当前 Chunk 时，本 Publisher 不会向上游请求更多事件，
     * 从而构成"按 Chunk 粒度"的天然背压。
     *
     * @param source 上游解析事件发布器，必须非空。
     * @param rule   切片规则，必须非空。
     * @return 仅透传 {@link ChunkedSection} 的发布器。
     * @throws NullPointerException 任一参数为 {@code null}。
     */
    public Flow.Publisher<ChunkedSection> adapt(Flow.Publisher<ReadEvent> source, ChunkingRule rule) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        return downstream -> adaptEvents(source, rule).subscribe(new FilteringSubscriber(downstream));
    }

    /**
     * 完整流式适配。一个输入文档对应一个 {@link StreamingChunker}，切片跨 parser section 连续进行；
     * Publisher 自己维护 demand，绝不将上游 Subscription 直接泄露给下游。
     * <p>
     * demand 转换语义：上游 {@code request(n)} 在本 Publisher 内部被转换为
     * "按下游剩余 demand 拉取上游事件"，上游的请求节奏由 {@link ChunkingSubscriber#drain()}
     * 统一调度；任何下游取消都会同步触发上游取消，避免资源泄漏。
     *
     * @param source 上游解析事件发布器，必须非空。
     * @param rule   切片规则，必须非空。
     * @return 同时承载 {@link ChunkingEvent.Section}、{@link ChunkingEvent.Finished}、
     * {@link ChunkingEvent.Failed} 三类事件的发布器。
     * @throws NullPointerException 任一参数为 {@code null}。
     */
    public Flow.Publisher<ChunkingEvent> adaptEvents(Flow.Publisher<ReadEvent> source, ChunkingRule rule) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        return downstream -> source.subscribe(new ChunkingSubscriber(downstream, rule));
    }

    /**
     * 流式适配核心订阅者：同时作为 {@link Flow.Subscriber} 消费上游
     * {@link ReadEvent}，又作为 {@link Flow.Subscription} 把下游 demand
     * 反向驱动到上游。
     * <p>
     * 一次订阅对应一份输入文档；session 内持有专用的 {@link StreamingChunker} 与
     * {@code sections} 列表，跨 session 互不污染；文档结束（{@link ReadEvent.Finished}
     * 或 {@link ReadEvent.Failed}）后即视为本 session 终止，再次订阅将分配全新实例。
     */
    private final class ChunkingSubscriber implements Flow.Subscriber<ReadEvent>, Flow.Subscription {

        /**
         * 下游消费者，所有 {@link ChunkingEvent} 都通过 {@code downstream.onNext} 发出。
         */
        private final Flow.Subscriber<? super ChunkingEvent> downstream;
        /**
         * 当前 session 的切片器，pending 缓冲在内部按 chunker 规则累积。
         */
        private final StreamingChunker chunker;
        /**
         * 已生产但尚未被下游消费的 {@link ChunkingEvent} 队列。
         */
        private final ArrayDeque<ChunkingEvent> pendingEvents = new ArrayDeque<>();
        /**
         * 已到达的 {@link ReadEvent.Section} 及其文档级偏移、文件名等元数据。
         */
        private final List<DocumentSection> sections = new ArrayList<>();

        /**
         * 上游 Subscription；首次 {@link #onSubscribe} 之后才被赋值。
         */
        private Flow.Subscription upstream;
        /**
         * 下游累计请求的 demand；{@link Long#MAX_VALUE} 表示不限速。
         */
        private long requested;
        /**
         * 下一个要分配的 section 顺序索引。
         */
        private int nextSectionIndex;
        /**
         * 当前文档的累计字符偏移，用于跨 section 的 SourceSpan 换算。
         */
        private int nextDocumentOffset;
        /**
         * 本 session 累计下发的 Chunk 数，写入 {@link ChunkingEvent.Finished#emittedChunks()}。
         */
        private int emittedChunks;
        /**
         * 上游是否还有一份未 ack 的请求（防 request 重入）。
         */
        private boolean upstreamOutstanding;
        /**
         * 上游是否已发出 {@code onComplete}。
         */
        private boolean upstreamCompleted;
        /**
         * 下游是否已调用 {@link #cancel()}；为 true 后所有回调都直接吞掉。
         */
        private boolean cancelled;
        /**
         * 本会话是否已发出过终止事件（{@code onError} / {@code onComplete} / Failed）。
         */
        private boolean terminalSent;

        /**
         * 构造一个 session；{@code chunker} 的 pending 上界取
         * {@code max(maxPendingCharacters, rule.maxCharacters())}，避免单 chunk 大小超过 pending。
         *
         * @param downstream 下游消费者，必须非空。
         * @param rule       本 session 的切片规则，决定 pending 缓冲下限。
         */
        private ChunkingSubscriber(Flow.Subscriber<? super ChunkingEvent> downstream, ChunkingRule rule) {
            this.downstream = Objects.requireNonNull(downstream, "downstream must not be null");
            this.chunker = new StreamingChunker(chunkingService, rule,
                    Math.max(maxPendingCharacters, rule.maxCharacters()));
        }

        /**
         * 上游首次回传 Subscription 时触发；保存 Subscription 并把本订阅者暴露给下游，
         * 同时按 reactive 契约丢弃重复订阅（{@code onSubscribe} 不可被多次调用）。
         */
        @Override
        public synchronized void onSubscribe(Flow.Subscription subscription) {
            if (upstream != null) {
                // 重复订阅属于上游违反 reactive 契约，主动取消以避免资源泄漏；
                // 这里不能简单忽略，否则旧 Subscription 仍会持续推送事件。
                subscription.cancel();
                return;
            }
            upstream = subscription;
            // 由本订阅者向 downstream 暴露自己，因此下游的 request/cancel 都先经过本类，
            // 构成"中间人"模式，确保上游 Subscription 永不直接下传。
            downstream.onSubscribe(this);
        }

        /**
         * 消费上游事件并转换为本会话事件，缓存到 {@link #pendingEvents} 后通过
         * {@link #drain()} 按下游 demand 节奏下发。
         * <p>
         * 事件分发约定：
         * <ul>
         *   <li>{@link ReadEvent.Section}：调 {@link #acceptSection}；</li>
         *   <li>{@link ReadEvent.Finished}：flush 当前 pending，再发
         *       {@link ChunkingEvent.Finished}；</li>
         *   <li>{@link ReadEvent.Failed}：先调 {@code chunker.abort()} 释放
         *       pending 内部状态，再发 {@link ChunkingEvent.Failed}，session 在该事件
         *       之后即视为终止；后续即便收到 {@code onComplete} / {@code onError} 也一律吞掉，
         *       因此不能用 {@code cancel()} 代替它——{@code cancel} 还会主动关闭上游
         *       Subscription，但 Failed 路径必须把错误事件如实下传给下游。</li>
         *   <li>{@link ReadEvent.Image}：被静默丢弃，不进入任何切片——切分纯文本 Chunk
         *       不需要图片二进制占位，下游也不应对其产生溯源期望。</li>
         * </ul>
         */
        @Override
        public synchronized void onNext(ReadEvent event) {
            if (cancelled || terminalSent) {
                return;
            }
            // 本次拉取已被上游投递；重置 outstanding 标志，便于 drain 继续请求上游。
            upstreamOutstanding = false;
            try {
                if (event instanceof ReadEvent.Section section) {
                    acceptSection(section);
                } else if (event instanceof ReadEvent.Finished finished) {
                    emitChunks(chunker.finish());
                    pendingEvents.addLast(new ChunkingEvent.Finished(finished.summary(),
                            finished.totalSections(), emittedChunks));
                } else if (event instanceof ReadEvent.Failed failed) {
                    // Failed 是终止信号，先放弃 chunker 内部 pending 状态避免泄漏，
                    // 再入队失败事件；不能简单 cancel——下游需要看到 Failed 才能正确收尾。
                    chunker.abort();
                    pendingEvents.addLast(new ChunkingEvent.Failed(failed.error()));
                }
                // Image 事件落入隐式 default：不做任何切片化处理，保持纯文本 Chunk 流水线纯净。
                drain();
            } catch (Throwable error) {
                // 任意处理异常都视作整 session 失败：取消上游、置取消位、把异常透传给下游，
                // 避免异常被默默吞掉后下游仍在等待事件。
                cancelled = true;
                upstream.cancel();
                downstream.onError(error);
            }
        }

        /**
         * 上游主动报错：放弃 chunker 内部 pending 并把异常透传给下游。
         * <p>
         * 与 {@link #onNext} 中 Failed 分支的区别：此处上游已不会再发任何事件，
         * 因此仅透传错误即可，不需要再额外发 {@link ChunkingEvent.Failed}；
         * 一旦终止事件下发即置 {@code terminalSent}，避免下游被多次通知。
         */
        @Override
        public synchronized void onError(Throwable throwable) {
            if (!cancelled && !terminalSent) {
                chunker.abort();
                terminalSent = true;
                downstream.onError(throwable);
            }
        }

        /**
         * 上游正常结束：先把 chunker 内部挂起的 Chunk flush 成 {@link ChunkingEvent.Section}，
         * 再由 {@link #drain()} 在所有 pending 排空后下发 {@code onComplete}。
         */
        @Override
        public synchronized void onComplete() {
            if (!cancelled && !terminalSent) {
                try {
                    emitChunks(chunker.finish());
                } catch (Throwable error) {
                    // flush 阶段抛错时优先选择 onError 通道，避免和正常 onComplete 语义混淆。
                    terminalSent = true;
                    downstream.onError(error);
                    return;
                }
            }
            upstreamCompleted = true;
            drain();
        }

        /**
         * 下游 demand 反向驱动：把下游请求量累加到 {@link #requested}，
         * 再由 {@link #drain()} 在已缓存的事件队列里尽可能多地下发；
         * 当 {@code requested} 仍有富余且上游未完成时，再按需调
         * {@code upstream.request(1)} 拉取下一条事件，实现按需背压。
         *
         * @param demand 下游单次请求的事件数，{@code <= 0} 视为非法。
         */
        @Override
        public synchronized void request(long demand) {
            if (demand <= 0) {
                // Reactive Streams §3.9 明确要求非正数 request 视作协议违规：
                // 这里直接取消上游并把错误透传给下游。
                cancel();
                downstream.onError(new IllegalArgumentException("request 数量必须大于 0"));
                return;
            }
            // 累加避免溢出：使用饱和加法近似 Long.MAX_VALUE，下游可借此表达"无限 demand"。
            requested = requested > Long.MAX_VALUE - demand ? Long.MAX_VALUE : requested + demand;
            drain();
        }

        /**
         * 下游取消：先打标记让后续回调短路，再清空 pending 缓冲并取消上游；
         * 重入取消通过 {@code cancelled} 位幂等短路。
         */
        @Override
        public synchronized void cancel() {
            if (cancelled) {
                return;
            }
            cancelled = true;
            chunker.abort();
            pendingEvents.clear();
            if (upstream != null) {
                upstream.cancel();
            }
        }

        /**
         * 接收一个上游 {@link ReadEvent.Section}：把当前 section 记入
         * {@link #sections}，并把整段文本喂给 {@link StreamingChunker}。
         * <p>
         * 跨 section 合并细节：每个 section 之间额外计 1 字符的"分隔偏移"
         * （例如 parser 在 section 之间补的分隔符），保证后续
         * {@link #sourceSpans(Chunk)} 把 chunk 的绝对区间反向换算到单 section
         * 内的下标时不会把跨段偏移量算进去。
         */
        private void acceptSection(ReadEvent.Section sectionEvent) {
            ParsedSection section = sectionEvent.section();
            if (!sections.isEmpty()) {
                // 跨 section 之间额外计 1 字符以反映 parser 在节间补的分隔符偏移，
                // 这样 chunk 的绝对下标与各 section 区间才不会错位重叠。
                nextDocumentOffset++;
            }
            int start = nextDocumentOffset;
            nextDocumentOffset += section.text().length();
            sections.add(new DocumentSection(nextSectionIndex++, sectionEvent.fileName(), section, start, nextDocumentOffset));
            // 切片器内部可能产出 0 到多个 Chunk；这里统一收齐后由 emitChunks 逐个入队。
            emitChunks(chunker.accept(section.text()));
        }

        /**
         * 把一组 chunk 包装成 {@link ChunkingEvent.Section} 入队。
         * <p>
         * 跨 section 的 chunk 处理：
         * <ul>
         *   <li>当 chunk 横跨多个 {@code DocumentSection} 时，{@link #sourceSpans(Chunk)}
         *       会按 section 边界拆分并各自换算成该 section 内部的相对下标；</li>
         *   <li>最终仅取首个 span 所在 section 作为本次 {@link ChunkedSection} 的
         *       {@code sectionIndex} / {@code fileName} / {@code source}，跨段 span 保留
         *       在 {@code sourceSpans} 中供下游引用；</li>
         *   <li>如果一个 chunk 在当前所有已收集 section 上都没有落点（极少见，多见于
         *       chunker 在 section 文本之外的缓冲区残留），则直接丢弃以避免产生空溯源事件。</li>
         * </ul>
         */
        private void emitChunks(List<Chunk> chunks) {
            for (Chunk chunk : chunks) {
                List<SourceSpan> spans = sourceSpans(chunk);
                if (spans.isEmpty()) {
                    // chunk 未命中任何已知 section，跳过以免发出无溯源意义的 ChunkedSection。
                    continue;
                }
                SourceSpan first = spans.getFirst();
                String fileName = sections.get(first.sectionIndex()).fileName();
                pendingEvents.addLast(new ChunkingEvent.Section(new ChunkedSection(first.sectionIndex(), fileName,
                        first.section(), new ChunkingResult(List.of(chunk)), spans)));
                emittedChunks++;
            }
        }

        /**
         * 计算一个 chunk 在当前已收集 section 序列中对应的 {@link SourceSpan} 列表。
         * <p>
         * 算法：对每个 section 取 chunk 与该 section 区间的交集，再换算成 section 内部
         * 的相对下标；交集为空（即 chunk 未触及本 section）的直接忽略。
         * 当 chunk 横跨多个 section 时，返回值按 section 出现顺序排列，
         * 第一个元素即为 {@link #emitChunks} 中选作 {@code sectionIndex} 的那一个。
         */
        private List<SourceSpan> sourceSpans(Chunk chunk) {
            List<SourceSpan> output = new ArrayList<>();
            for (DocumentSection section : sections) {
                int start = Math.max(chunk.charStart(), section.start());
                int end = Math.min(chunk.charEnd(), section.end());
                if (start < end) {
                    // 把绝对区间换算回 section 内部下标 (start - section.start(), end - section.start())。
                    output.add(new SourceSpan(section.index(), section.section(), start - section.start(), end - section.start()));
                }
            }
            return List.copyOf(output);
        }

        /**
         * 统一的发送闸口：尽可能多地从 {@link #pendingEvents} 下发事件给下游，
         * 直到 demand 用尽或队列清空；之后判断是否需要收尾（{@code onComplete}）
         * 或继续从上游拉取（{@code upstream.request(1)}）。
         * <p>
         * 调用时机：{@link #onNext} 处理完一个上游事件后、{@link #onError} /
         * {@link #onComplete} 收尾前、{@link #request(long)} 累加 demand 后。
         * 该方法内部已正确处理 demand 上限、终止位、去重拉取三个边界，外部调用方
         * 无需再自行判断"还能不能发"。
         */
        private void drain() {
            while (!cancelled && requested > 0 && !pendingEvents.isEmpty()) {
                ChunkingEvent event = pendingEvents.removeFirst();
                if (requested != Long.MAX_VALUE) {
                    requested--;
                }
                downstream.onNext(event);
            }
            // 收尾条件：上游已完成 + 本会话所有 pending 已发完 + 还未下发过终止事件
            // → 此时才向下游发出 onComplete，确保下游能完整看到所有 Section/Finished/Failed。
            if (!cancelled && upstreamCompleted && pendingEvents.isEmpty() && !terminalSent) {
                terminalSent = true;
                downstream.onComplete();
                return;
            }
            // 续拉条件：下游还有未消耗 demand 且没有未 ack 的上游请求（防 request 重入）。
            if (!cancelled && !upstreamCompleted && requested > 0 && !upstreamOutstanding) {
                upstreamOutstanding = true;
                upstream.request(1);
            }
        }
    }

    /**
     * 仅透传 {@link ChunkingEvent.Section} 的二级订阅者；屏蔽
     * {@link ChunkingEvent.Finished} 与 {@link ChunkingEvent.Failed}，
     * 用于 {@link ParserChunkingAdapter#adapt} 入口。
     * <p>
     * 过滤策略：
     * <ul>
     *   <li>仅 {@code ChunkingEvent.Section} 被放行并把内层 {@link ChunkedSection}
     *       透传给下游，其它类型直接丢弃；这是 {@code adapt} 入口的语义——下游只关心
     *       已产出的切片，不关心文档级完成或失败事件；</li>
     *   <li>屏蔽 {@link ChunkingEvent.Finished} 时不会因为"信号丢失"导致下游永驻，
     *       本类不向下游发任何终止信号，而是依赖上游最终走 {@code onComplete} 完成收尾；</li>
     *   <li>屏蔽 {@link ChunkingEvent.Failed} 属于主动决策——下游既不应被告知失败（避免
     *       误以为切片丢失），也不应继续按正常路径 await 终止事件，由上游 {@code onComplete}
     *       自然终结整条链。</li>
     * </ul>
     * 之所以能在不丢信号的前提下做到这一点，是因为本类背后实际订阅的是
     * {@code adaptEvents} 的输出——后者会按 reactive 协议在下发 Failed 后继续按需
     * 透传 {@code onError} / {@code onComplete}，本类只是把"业务事件"维度做了收窄。
     */
    private static final class FilteringSubscriber implements Flow.Subscriber<ChunkingEvent>, Flow.Subscription {

        private final Flow.Subscriber<? super ChunkedSection> downstream;
        private Flow.Subscription upstream;
        private long requested;
        private boolean cancelled;

        private FilteringSubscriber(Flow.Subscriber<? super ChunkedSection> downstream) {
            this.downstream = Objects.requireNonNull(downstream, "downstream must not be null");
        }

        /**
         * 缓存上游 Subscription 并向下游暴露自己；本订阅者作为中间人控制节奏，
         * 不会把上游 Subscription 直接下传给下游。
         */
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.upstream = subscription;
            downstream.onSubscribe(this);
        }

        /**
         * 仅放行 {@link ChunkingEvent.Section}：触发时递减 demand 并把
         * {@link ChunkedSection} 透传给下游；其它类型的事件静默丢弃。
         * 之后只要下游还有 demand 且未被取消，立即向上游再请求一个事件，
         * 保证逐元素背压不被本包装层放大或节流。
         */
        @Override
        public void onNext(ChunkingEvent event) {
            if (cancelled) {
                return;
            }
            if (event instanceof ChunkingEvent.Section section) {
                // 仅 Section 消耗一份 demand；Finished/Failed 不计入 demand，
                // 保证下游 request(n) 与"实际拿到的 Chunk"数量语义一致。
                if (requested != Long.MAX_VALUE) {
                    requested--;
                }
                downstream.onNext(section.value());
            }
            if (!cancelled && requested > 0) {
                upstream.request(1);
            }
        }

        /**
         * 透传上游错误：不在此处额外合成 {@link ChunkingEvent.Failed}，
         * 避免与上游的失败信号叠加造成下游重复感知。
         */
        @Override
        public void onError(Throwable throwable) {
            if (!cancelled) {
                downstream.onError(throwable);
            }
        }

        /**
         * 透传上游结束：屏蔽 {@link ChunkingEvent.Finished} 不会让本订阅者
         * 永远停在中间——上游自己会在下发 Finished 后正常调用 {@code onComplete}，
         * 本订阅者继续按 reactive 协议把它透传给下游。
         */
        @Override
        public void onComplete() {
            if (!cancelled) {
                downstream.onComplete();
            }
        }

        /**
         * 下游 demand 反向驱动：累加后立刻向上游请求 1 个事件；本包装层不对
         * demand 做批量化，以便下游能够精确控制"每消费一个 Chunk 就拉下一个"的节奏。
         *
         * @param demand 下游单次请求的事件数，{@code <= 0} 视为非法。
         */
        @Override
        public void request(long demand) {
            if (demand <= 0) {
                cancel();
                downstream.onError(new IllegalArgumentException("request 数量必须大于 0"));
                return;
            }
            requested = requested > Long.MAX_VALUE - demand ? Long.MAX_VALUE : requested + demand;
            // 不等待内部缓冲区逐个发出，直接触发一次上游拉取，
            // 减少下游等待时间同时避免与上游 outstanding 机制冲突。
            upstream.request(1);
        }

        /**
         * 下游取消：打标记后立即向上游取消，避免后续上游推送的事件被静默丢弃却
         * 占用上游解析器资源。
         */
        @Override
        public void cancel() {
            cancelled = true;
            if (upstream != null) {
                upstream.cancel();
            }
        }
    }

    /**
     * 单一 section 在文档级坐标系中的描述，仅供 {@link ChunkingSubscriber#sourceSpans(Chunk)}
     * 计算跨 section chunk 的 {@link SourceSpan} 列表时使用。
     *
     * @param index    section 顺序下标，与 {@code ChunkedSection.sectionIndex} 对齐。
     * @param fileName 来源文件名（来自 {@code ReadEvent.Section.fileName()}），可能为 {@code null}。
     * @param section  上游 {@link ParsedSection}，不会为 {@code null}。
     * @param start    该 section 在文档累计坐标系中的起始字符偏移（含）。
     * @param end      该 section 在文档累计坐标系中的结束字符偏移（不含）。
     */
    private record DocumentSection(int index, String fileName, ParsedSection section, int start, int end) {
    }
}
