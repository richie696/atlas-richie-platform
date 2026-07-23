/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.ai.provider.tei;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TeiImageEmbeddingAdapter} 的纯单元测试（hermetic —— 不发起真实 TEI 调用）。
 *
 * <p>策略：mock {@link HttpClient} + {@link HttpRequest}，把 {@code request.execute(Class)}
 * 桩成返回预设 JSON 反序列化结果。覆盖：
 * <ul>
 *   <li>OpenAI 兼容协议的输入 / 输出映射（单文本 / 批量 / 按 index 重排）</li>
 *   <li>可选 Bearer 鉴权头（设置 / 不设置两种路径）</li>
 *   <li>空响应 / 异常路径下的占位行为</li>
 *   <li>{@link TeiImageEmbeddingAdapter#dimensions()} 返回 0（未知编译期维度）</li>
 *   <li>{@link TeiImageEmbeddingAdapter#embed(Document)} 对无文本文档的空向量兜底</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
class TeiImageEmbeddingAdapterTest {

    @Test
    void call_singleText_returnsEmbeddingFromDataArray() {
        String json = """
                {
                  "data": [
                    {"embedding": [0.0123, -0.0456, 0.0789], "index": 0}
                  ],
                  "model": "BAAI/bge-large-en-v1.5"
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", null, null);

        EmbeddingResponse resp = adapter.call(new EmbeddingRequest(List.of("hello"), null));

assertThat(resp).isNotNull();
        assertThat(resp.getResults()).hasSize(1);
        assertThat(resp.getResults().get(0).getOutput()).containsExactly(0.0123f, -0.0456f, 0.0789f);
        assertThat(resp.getResults().get(0).getIndex()).isEqualTo(0);
    }

    @Test
    void call_batchTexts_returnsMultipleEmbeddingsByIndex() {
        String json = """
                {
                  "data": [
                    {"embedding": [0.1, 0.2, 0.3], "index": 0},
                    {"embedding": [0.4, 0.5, 0.6], "index": 1},
                    {"embedding": [0.7, 0.8, 0.9], "index": 2}
                  ]
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", null, null);

        EmbeddingResponse resp = adapter.call(new EmbeddingRequest(List.of("a", "b", "c"), null));

        assertThat(resp.getResults()).hasSize(3);
        assertThat(resp.getResults().get(0).getOutput()[0]).isEqualTo(0.1f, within(1e-6f));
        assertThat(resp.getResults().get(1).getOutput()[1]).isEqualTo(0.5f, within(1e-6f));
        assertThat(resp.getResults().get(2).getOutput()[2]).isEqualTo(0.9f, within(1e-6f));
        // 三个结果按 index 对齐到三个请求槽位
        assertThat(resp.getResults().get(0).getIndex()).isEqualTo(0);
        assertThat(resp.getResults().get(1).getIndex()).isEqualTo(1);
        assertThat(resp.getResults().get(2).getIndex()).isEqualTo(2);
    }

    @Test
    void call_withApiKey_addsAuthorizationBearerHeader() {
        String json = """
                {
                  "data": [
                    {"embedding": [0.1], "index": 0}
                  ]
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", null, "sk-test-key");

        adapter.call(new EmbeddingRequest(List.of("hello"), null));

        // 验证 Bearer 头被正确注入
        verify(fx.request).header("Authorization", "Bearer sk-test-key");
    }

    @Test
    void call_withoutApiKey_doesNotAddAuthorizationHeader() {
        String json = """
                {
                  "data": [
                    {"embedding": [0.1], "index": 0}
                  ]
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", null, null);

        adapter.call(new EmbeddingRequest(List.of("hello"), null));

        // apiKey=null 时绝对不能携带 Authorization 头 —— TEI 本身不要求鉴权,
        // 业务侧若不需要反代鉴权则不应有多余请求头。
        verify(fx.request, never()).header(eq("Authorization"), anyString());
    }

    @Test
    void call_emptyDataArray_returnsZeroVectorPlaceholders() {
        String json = """
                {
                  "data": []
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", null, null);

        EmbeddingResponse resp = adapter.call(new EmbeddingRequest(List.of("a", "b", "c"), null));

        // 即使 data 为空,返回的 Embedding 数量仍与请求数量一致 —— 上层可按 size 一致性判定。
        assertThat(resp.getResults()).hasSize(3);
        // TEI 维度未知,占位为空向量 —— 业务侧应识别"全 0 / 空 = 无意义"条目。
        assertThat(resp.getResults().get(0).getOutput()).isEmpty();
        assertThat(resp.getResults().get(1).getOutput()).isEmpty();
        assertThat(resp.getResults().get(2).getOutput()).isEmpty();
        assertThat(resp.getResults().get(0).getIndex()).isEqualTo(0);
        assertThat(resp.getResults().get(1).getIndex()).isEqualTo(1);
        assertThat(resp.getResults().get(2).getIndex()).isEqualTo(2);
    }

    @Test
    void call_httpError_propagatesRuntimeException() {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest httpRequest = mock(HttpRequest.class);
        when(httpClient.post(anyString(), any())).thenReturn(httpRequest);
        when(httpRequest.header(anyString(), anyString())).thenReturn(httpRequest);
        when(httpRequest.execute(org.mockito.ArgumentMatchers.<Class<Object>>any()))
                .thenThrow(new RuntimeException("TEI upstream connection refused"));

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                httpClient, "http://localhost:8080", null, null);

        assertThatThrownBy(() -> adapter.call(new EmbeddingRequest(List.of("hello"), null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("TEI upstream connection refused");
    }

    @Test
    void embed_documentWithoutText_returnsEmptyVectorWithWarn() {
        HttpClient httpClient = mock(HttpClient.class);
        // 模拟一个 getText() 返回 null 的 Document —— Spring AI 的 Document 在
        // 持有图像 (Media) 时 getText() 会返回 null,表示无可向量化的文本分支。
        Document doc = mock(Document.class);
        when(doc.getText()).thenReturn(null);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                httpClient, "http://localhost:8080", null, null);

        float[] vec = adapter.embed(doc);

        // 兜底:返回空向量而不是抛错,让上层 vector store 仍能走到写入流程。
        // 由于 TEI 维度编译期未知,占位是空数组而非固定长度零向量 —— 与 Bailian
        // 路径(已知 DIMENSIONS=1024)的语义差异在 JavaDoc 中已说明。
        assertThat(vec).isNotNull().isEmpty();
        // 文档无文本时不应对 TEI 发起任何请求 —— 网络调用是浪费
        verify(httpClient, never()).post(anyString(), any());
    }

    @Test
    void dimensions_returnsZeroForUnknownModel() {
        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                mock(HttpClient.class), "http://localhost:8080", null, null);

        // TEI 维度由运行时部署模型决定,编译期不可知 —— 直接返回 0,
        // 避免 Spring AI 默认实现做一次计费 probe 调用。
        assertThat(adapter.dimensions()).isEqualTo(0);
    }

    // -------- optional extras: 边界条件覆盖 --------

    @Test
    void call_sendsOpenAiCompatibleBodyWithInputArray() {
        String json = """
                {
                  "data": [
                    {"embedding": [0.5], "index": 0}
                  ]
                }
                """;
        RequestFixture fx = fixture(json);

        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                fx.httpClient, "http://localhost:8080", "BAAI/bge-large-en-v1.5", null);

        adapter.call(new EmbeddingRequest(List.of("hello"), null));

        // 抓取请求体 Map 验证 OpenAI 兼容形态:input 始终是数组 + 可选 model
        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(fx.httpClient).post(eq("http://localhost:8080/v1/embeddings"), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        assertThat(body).containsKey("input");
        Object inputVal = body.get("input");
        assertThat(inputVal).isInstanceOf(List.class);
        List<?> inputList = (List<?>) inputVal;
        assertThat(inputList).hasSize(1);
        assertThat(inputList.get(0)).isEqualTo("hello");
        assertThat(body).containsEntry("model", "BAAI/bge-large-en-v1.5");
    }

    @Test
    void call_blankBaseUrl_defaultsToLocalhost8080() throws Exception {
        TeiImageEmbeddingAdapter adapter = new TeiImageEmbeddingAdapter(
                mock(HttpClient.class), null, null, null);

        java.lang.reflect.Field baseUrl = TeiImageEmbeddingAdapter.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        assertThat(baseUrl.get(adapter)).isEqualTo(TeiImageEmbeddingAdapter.DEFAULT_BASE_URL);
    }

    // -------- helpers --------

    /** HTTP 桩组合:把假 JSON 反序列化为 {@link TeiImageEmbeddingAdapter.TeiEmbeddingRawResponse}。 */
    private static RequestFixture fixture(String json) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        when(httpClient.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.execute(org.mockito.ArgumentMatchers.<Class<Object>>any())).thenAnswer(invocation -> {
            Class<Object> responseType = invocation.getArgument(0);
            return JsonUtils.getInstance().deserialize(json, responseType);
        });
        return new RequestFixture(httpClient, request);
    }

    /** HTTP 桩组合。 */
    private record RequestFixture(HttpClient httpClient, HttpRequest request) {
    }
}