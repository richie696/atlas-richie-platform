package cn.richie696.component.chunking.semantic;

import cn.richie696.component.chunking.spi.SemanticBoundaryAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 可选 Spring AI 桥接；模型实例由调用方传入，不读取任何模型配置。
 * <p>
 * 本类属于 {@code atlas-richie-component-document-chunking-semantic-spring-ai} 可选模块，
 * 是 {@link SemanticBoundaryAdvisor} SPI 的一种 ChatModel 实现：
 * <ul>
 *   <li>上游：把待切分的整段文本拼成"只输出 CSV 边界"的 prompt，丢给调用方注入的
 *       {@link ChatModel}；</li>
 *   <li>下游：把模型返回的字符串解析成 {@code List<Integer>}（字符下标列表），供
 *       {@code SemanticChunkingService} 做去重、排序、越界过滤。</li>
 * </ul>
 *
 * <p>与 {@link SemanticBoundaryAdvisor} SPI 的契约边界：本类只负责"从模型到
 * {@code List<Integer>}"的桥接，不持有任何业务规则、模型配置或上下文；调用方负责
 * 注入 {@link ChatModel} 实例，并自行决定超时、重试与降级策略。
 *
 * <p>失败约定：模型返回不完整（{@code ChatResponse} / {@code Generation} / {@code AssistantMessage}
 * 为 {@code null}）时，原样向上抛 {@link NullPointerException}（不包装、不吞掉），由调用方决定
 * 后续策略；模型返回空文本或纯空白文本时，本桥接返回空列表，由上层走
 * {@code SemanticChunkingService} 的降级路径或视为"无候选边界"。
 *
 * @author richie696
 * @since 2026-07-27
 */
public final class SpringAiSemanticBoundaryAdvisor implements SemanticBoundaryAdvisor {
    private final ChatModel model;

    /**
     * 构造一个绑定到指定 {@link ChatModel} 的 advisor；模型的生命周期、超时与凭证由调用方管理。
     *
     * @param model Spring AI {@link ChatModel} 实例，必须非空。
     * @throws NullPointerException 当 {@code model} 为 {@code null} 时。
     */
    public SpringAiSemanticBoundaryAdvisor(ChatModel model) {
        this.model = Objects.requireNonNull(model);
    }

    /**
     * 让模型为整段文本产出候选语义边界下标。
     * <p>
     * 时序：
     * <ol>
     *   <li>把"只输出 CSV 字符下标"的指令与正文拼成一个 {@link Prompt}；</li>
     *   <li>同步调用 {@link ChatModel#call(Prompt)}，取得文本输出；</li>
     *   <li>按逗号切分，逐项解析为整数；越界或格式错误项直接丢弃；</li>
     *   <li>按去重、升序排序后返回。</li>
     * </ol>
     *
     * <p>边界过滤规则（{@code v > 0 && v < content.length()}）：排除 0（首字符之前）与
     * {@code content.length()}（末字符之后）这两个不可用下标，避免下游切片器拿到
     * 会触发 {@code StringIndexOutOfBoundsException} 的非法区间。
     *
     * @param content 待切分的完整文本（不为 {@code null}，调用方负责）。
     * @return 候选字符下标列表，已去重并升序排列；模型返回空文本或纯空白文本时返回空列表。
     * @throws NullPointerException 当 {@code ChatResponse} / {@code Generation} / {@code AssistantMessage} 为 {@code null} 时原样抛出。
     */
    @Override
    public List<Integer> boundaries(String content) {
        String instruction = "Return only comma-separated zero-based character offsets at semantic section boundaries for this text. Do not explain. Text:\n" + content;
        ChatResponse response = model.call(new Prompt(instruction));
        // 此处有意不补 null 检查：调用方传入 null ChatResponse/getResult/getOutput 时
        // 由 JDK 链式访问原地抛 NPE，调用方按合约捕获；空文本/纯空白输出回退为空列表，
        // 由上游 SemanticChunkingService 走降级路径或视为"无候选边界"。
        String output = response.getResult().getOutput().getText();
        if (output == null || output.isBlank()) return List.of();
        List<Integer> values = new ArrayList<>();
        for (String item : output.split(","))
            try {
                int v = Integer.parseInt(item.trim());
                if (v > 0 && v < content.length()) values.add(v);
            } catch (NumberFormatException ignored) {
            }
        // distinct+sorted 双重保险：模型可能重复给出同一边界（同一处主题反复强调），
        // 也可能不严格按升序输出；下游 chunker 依赖升序边界，排序在这里兜底一次。
        return values.stream().distinct().sorted().toList();
    }
}
