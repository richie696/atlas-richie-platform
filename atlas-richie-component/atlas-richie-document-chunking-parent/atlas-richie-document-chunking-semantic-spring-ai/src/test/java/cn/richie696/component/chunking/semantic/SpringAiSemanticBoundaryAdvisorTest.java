package cn.richie696.component.chunking.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SpringAiSemanticBoundaryAdvisor} 单元测试：仅 mock ChatModel 调用链，不连接任何真实 LLM。
 */
@ExtendWith(MockitoExtension.class)
class SpringAiSemanticBoundaryAdvisorTest {

    @Mock
    private ChatModel chatModel;
    @Mock
    private ChatResponse chatResponse;
    @Mock
    private Generation generation;
    @Mock
    private AssistantMessage assistantMessage;

    private SpringAiSemanticBoundaryAdvisor advisor;

    @BeforeEach
    void setUp() {
        advisor = new SpringAiSemanticBoundaryAdvisor(chatModel);
    }

    /**
     * 让 ChatModel 调用链按 ChatResponse → Generation → AssistantMessage → text 的顺序串联返回指定文本。
     */
    private void stubChatModelOutput(String text) {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(text);
    }

    @Test
    @DisplayName("构造器传入 null ChatModel 应抛 NullPointerException")
    void constructor_nullChatModel_shouldThrowNullPointerException() {
        assertThatThrownBy(() -> new SpringAiSemanticBoundaryAdvisor(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("正常 CSV 输出应解析为有序且去重的偏移列表")
    void boundaries_normalCsvOutput_shouldReturnSortedDistinctOffsets() {
        stubChatModelOutput("5,12,30");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12, 30);
    }

    @Test
    @DisplayName("重复值应在结果中去重")
    void boundaries_duplicateValues_shouldBeDeduplicated() {
        stubChatModelOutput("5,5,12,12,30");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12, 30);
    }

    @Test
    @DisplayName("0 与超出 content.length 的值应被过滤")
    void boundaries_zeroAndOutOfRangeValues_shouldBeFiltered() {
        stubChatModelOutput("0,5,12,100");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12);
    }

    @Test
    @DisplayName("负数偏移应被过滤")
    void boundaries_negativeValues_shouldBeFiltered() {
        stubChatModelOutput("-5,5,12");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12);
    }

    @Test
    @DisplayName("乱序输入应按升序排序输出")
    void boundaries_unsortedInput_shouldBeSortedAscending() {
        stubChatModelOutput("30,5,12");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12, 30);
    }

    @Test
    @DisplayName("非整数 token（字母、浮点）应被跳过")
    void boundaries_nonIntegerTokens_shouldBeSkipped() {
        stubChatModelOutput("abc,5,3.14,12");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12);
    }

    @Test
    @DisplayName("空 token 与带空白 token 应被 trim 后正确解析")
    void boundaries_emptyAndWhitespaceTokens_shouldBeTrimmedOrSkipped() {
        stubChatModelOutput(" 5 , 12 ,  30 ");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 12, 30);
    }

    @Test
    @DisplayName("空字符串输出应返回空列表")
    void boundaries_emptyStringOutput_shouldReturnEmptyList() {
        stubChatModelOutput("");
        String content = "0".repeat(20);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("AssistantMessage 文本为 null 时应返回空列表")
    void boundaries_nullAssistantText_shouldReturnEmptyList() {
        stubChatModelOutput(null);
        String content = "0".repeat(20);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("纯空白输出应返回空列表")
    void boundaries_whitespaceOnlyOutput_shouldReturnEmptyList() {
        stubChatModelOutput("   ");
        String content = "0".repeat(20);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Prompt 文案应包含语义边界指令与原始 content")
    void boundaries_shouldSendPromptWithInstructionAndContent() {
        stubChatModelOutput("5");
        String content = "0123456789";

        advisor.boundaries(content);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String rendered = captor.getValue().getContents();
        assertThat(rendered).contains("semantic section boundaries");
        assertThat(rendered).contains(content);
    }

    @Test
    @DisplayName("ChatModel 抛异常时应向上原样传播")
    void boundaries_chatModelThrows_shouldPropagateException() {
        RuntimeException expected = new RuntimeException("chat-model-down");
        when(chatModel.call(any(Prompt.class))).thenThrow(expected);
        String content = "0".repeat(20);

        assertThatThrownBy(() -> advisor.boundaries(content))
                .isInstanceOf(RuntimeException.class)
                .isSameAs(expected);
    }

    // ============================================================================
    // 扩展测试：null/空/边界值/超长输入/CSV 边缘格式等"非常规路径"。
    // 既有 13 个测试保持不变，仅追加新方法。
    // ============================================================================

    @Test
    @DisplayName("content 为 null 时应抛 NPE（在 content.length() 阶段）")
    void boundaries_nullContent_shouldThrowNullPointerException() {
        stubChatModelOutput("5");
        assertThatNullPointerException()
                .isThrownBy(() -> advisor.boundaries(null));
    }

    @Test
    @DisplayName("content 为空串时所有偏移越界被过滤 → 返回空列表")
    void boundaries_emptyContent_shouldReturnEmptyList() {
        stubChatModelOutput("1,2,3");
        List<Integer> result = advisor.boundaries("");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("ChatResponse.getResult() 为 null 时抛 NPE（实现未防御，记录当前行为）")
    void boundaries_chatResponseGetResultIsNull_shouldThrowNullPointerException() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(null);
        String content = "0".repeat(20);

        assertThatNullPointerException()
                .isThrownBy(() -> advisor.boundaries(content));
    }

    @Test
    @DisplayName("Generation.getOutput() 为 null 时抛 NPE（实现未防御，记录当前行为）")
    void boundaries_generationGetOutputIsNull_shouldThrowNullPointerException() {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(null);
        String content = "0".repeat(20);

        assertThatNullPointerException()
                .isThrownBy(() -> advisor.boundaries(content));
    }

    @Test
    @DisplayName("CSV 含负数+重复+越界+合法时应仅返回去重升序的合法偏移")
    void boundaries_mixedNegativesDuplicatesOutOfRangeAndValidCsv_shouldReturnSortedDistinctValidOffsets() {
        stubChatModelOutput("-3,0,5,5,30,100");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(5, 30);
    }

    @Test
    @DisplayName("CSV 含非数字 token（foo）时应跳过，仅保留整数")
    void boundaries_csvWithFooToken_shouldSkipNonNumericAndKeepNumbers() {
        stubChatModelOutput("12,foo,25");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(12, 25);
    }

    @Test
    @DisplayName("CSV token 前后多空格应被 trim 后正确解析")
    void boundaries_csvWithExtraSurroundingWhitespace_shouldTrimAndParseAllNumbers() {
        stubChatModelOutput("  12  ,  25  ,80");
        String content = "0".repeat(100);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(12, 25, 80);
    }

    @Test
    @DisplayName("CSV 末尾有逗号时应忽略空 token，不抛 NPE")
    void boundaries_csvWithTrailingComma_shouldNotThrowAndIgnoreEmptyToken() {
        stubChatModelOutput("12,25,");
        String content = "0".repeat(40);

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(12, 25);
    }

    @Test
    @DisplayName("Prompt 文案应包含原始 content（不被截断）")
    void boundaries_promptContentsShouldIncludeFullOriginalContent() {
        stubChatModelOutput("5");
        String content = "原始文档片段-用于校验全文透传";

        advisor.boundaries(content);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String rendered = captor.getValue().getContents();
        assertThat(rendered).contains("semantic section boundaries");
        assertThat(rendered).contains(content);
        assertThat(rendered).endsWith(content);
    }

    @Test
    @DisplayName("ChatModel.call 抛 RuntimeException 时应透传同一实例（不包装）")
    void boundaries_chatModelCallThrowsRuntimeException_shouldPropagateExactSameInstance() {
        RuntimeException expected = new IllegalStateException("upstream-timeout");
        when(chatModel.call(any(Prompt.class))).thenThrow(expected);
        String content = "0".repeat(20);

        assertThatThrownBy(() -> advisor.boundaries(content))
                .isSameAs(expected)
                .hasMessage("upstream-timeout");
    }

    @Test
    @DisplayName("超长 content（100k 字符）应完整送入 prompt，不应被截断")
    void boundaries_veryLongContent_shouldNotBeTruncatedInPrompt() {
        stubChatModelOutput("5");
        StringBuilder sb = new StringBuilder(100_000);
        for (int i = 0; i < 100_000; i++) {
            sb.append((char) ('a' + (i % 26)));
        }
        String content = sb.toString();

        advisor.boundaries(content);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String rendered = captor.getValue().getContents();
        assertThat(rendered).contains(content);
        assertThat(rendered.length()).isGreaterThanOrEqualTo(content.length());
    }

    @Test
    @DisplayName("边界值 v == content.length() 应被过滤（严格 <），v < content.length() 才合法")
    void boundaries_boundaryValueAtContentLength_shouldBeExcluded() {
        stubChatModelOutput("2,3,4,5");
        String content = "abc";

        List<Integer> result = advisor.boundaries(content);

        assertThat(result).containsExactly(2);
    }
}