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
package com.richie.component.ai.provider.ollama;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OllamaImageEmbeddingAdapter} 的纯单元测试，不连接真实 Ollama 服务。
 *
 * <p>测试按 {@code BailianImageEmbeddingAdapterTest} 的方式 mock {@link HttpClient} 与
 * {@link HttpRequest}，并用项目 {@link JsonUtils} 把预置 JSON 反序列化为适配器响应 DTO。
 *
 * @author richie696
 * @since 2026-07-22
 */
class OllamaImageEmbeddingAdapterTest {

    @Test
    void call_singleText_returnsEmbedding() {
        RequestFixture fixture = fixture("""
                {
                  "model": "nomic-embed-text",
                  "embeddings": [[0.125, -0.25, 0.5]],
                  "total_duration": 123456,
                  "load_duration": 1234
                }
                """);
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                fixture.httpClient, "http://ollama.internal:11434/", null);

        EmbeddingResponse response = adapter.call(
                new EmbeddingRequest(List.of("hello ollama"), null));

        assertThat(response.getResults()).hasSize(1);
        assertThat(response.getResults().get(0).getIndex()).isZero();
        float[] output = response.getResults().get(0).getOutput();
        assertThat(output).hasSize(OllamaImageEmbeddingAdapter.DEFAULT_DIMENSIONS);
        assertThat(output[0]).isEqualTo(0.125f, within(1e-6f));
        assertThat(output[1]).isEqualTo(-0.25f, within(1e-6f));
        assertThat(output[2]).isEqualTo(0.5f, within(1e-6f));
        assertThat(output[3]).isZero();

        verify(fixture.httpClient).post(
                "http://ollama.internal:11434/api/embed",
                Map.of("model", "nomic-embed-text", "input", "hello ollama"));
        verify(fixture.request).header("Content-Type", "application/json");
    }

    @Test
    void call_batchTexts_returnsMultipleEmbeddings() {
        RequestFixture fixture = fixture("""
                {
                  "model": "clip",
                  "embeddings": [
                    [0.1, 0.2],
                    [0.3, 0.4],
                    [0.5, 0.6]
                  ]
                }
                """);
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                fixture.httpClient, null, "clip");
        List<String> inputs = List.of("first", "second", "third");

        EmbeddingResponse response = adapter.call(new EmbeddingRequest(inputs, null));

        assertThat(response.getResults()).hasSize(3);
        assertThat(response.getResults()).extracting(result -> result.getIndex())
                .containsExactly(0, 1, 2);
        assertThat(response.getResults().get(0).getOutput()[0]).isEqualTo(0.1f, within(1e-6f));
        assertThat(response.getResults().get(1).getOutput()[0]).isEqualTo(0.3f, within(1e-6f));
        assertThat(response.getResults().get(2).getOutput()[1]).isEqualTo(0.6f, within(1e-6f));
        assertThat(response.getResults())
                .allSatisfy(result -> assertThat(result.getOutput())
                        .hasSize(OllamaImageEmbeddingAdapter.DEFAULT_DIMENSIONS));

        verify(fixture.httpClient).post(
                OllamaImageEmbeddingAdapter.DEFAULT_BASE_URL + "/api/embed",
                Map.of("model", "clip", "input", inputs));
    }

    @Test
    void call_emptyInputs_returnsEmptyResponseWithWarning() {
        HttpClient httpClient = mock(HttpClient.class);
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                httpClient, null, null);

        EmbeddingResponse response = adapter.call(new EmbeddingRequest(List.of(), null));

        assertThat(response.getResults()).isEmpty();
        verify(httpClient, never()).post(anyString(), any());
    }

    @Test
    void call_httpError_propagatesException() {
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        IllegalStateException cause = new IllegalStateException("connection refused");
        when(httpClient.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.execute(org.mockito.ArgumentMatchers.<Class<Object>>any()))
                .thenThrow(cause);
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                httpClient, null, null);

        assertThatThrownBy(() -> adapter.call(
                new EmbeddingRequest(List.of("will fail"), null)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Ollama image embedding request failed")
                .hasCause(cause);
    }

    @Test
    void embed_documentWithText_usesTextBranch() {
        RequestFixture fixture = fixture("""
                {"embeddings": [[0.7, 0.8, 0.9]]}
                """);
        Document document = mock(Document.class);
        when(document.getText()).thenReturn("document text");
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                fixture.httpClient, null, null);

        float[] output = adapter.embed(document);

        assertThat(output).hasSize(OllamaImageEmbeddingAdapter.DEFAULT_DIMENSIONS);
        assertThat(output[0]).isEqualTo(0.7f, within(1e-6f));
        assertThat(output[2]).isEqualTo(0.9f, within(1e-6f));
        verify(fixture.httpClient).post(
                OllamaImageEmbeddingAdapter.DEFAULT_BASE_URL + "/api/embed",
                Map.of("model", OllamaImageEmbeddingAdapter.DEFAULT_MODEL,
                        "input", "document text"));
    }

    @Test
    void embed_documentWithoutText_returnsZeroVector() {
        HttpClient httpClient = mock(HttpClient.class);
        Document document = mock(Document.class);
        when(document.getText()).thenReturn("   ");
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                httpClient, null, null);

        float[] output = adapter.embed(document);

        assertThat(output).hasSize(OllamaImageEmbeddingAdapter.DEFAULT_DIMENSIONS)
                .containsOnly(0f);
        verify(httpClient, never()).post(anyString(), any());
    }

    @Test
    void dimensions_returns768() {
        HttpClient httpClient = mock(HttpClient.class);
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                httpClient, null, null);

        assertThat(adapter.dimensions()).isEqualTo(768);
        assertThat(adapter.dimensions()).isEqualTo(OllamaImageEmbeddingAdapter.DEFAULT_DIMENSIONS);
        verify(httpClient, never()).post(anyString(), any());
    }

    @Test
    void constructor_blankBaseUrl_usesDefaultLocalhost() throws Exception {
        OllamaImageEmbeddingAdapter adapter = new OllamaImageEmbeddingAdapter(
                mock(HttpClient.class), "   ", "   ");

        Field baseUrl = OllamaImageEmbeddingAdapter.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        Field defaultModel = OllamaImageEmbeddingAdapter.class.getDeclaredField("defaultModel");
        defaultModel.setAccessible(true);

        assertThat(baseUrl.get(adapter)).isEqualTo(OllamaImageEmbeddingAdapter.DEFAULT_BASE_URL);
        assertThat(defaultModel.get(adapter)).isEqualTo(OllamaImageEmbeddingAdapter.DEFAULT_MODEL);
    }

    /**
     * 创建与 Bailian 测试相同形态的 HTTP 桩。
     *
     * @param json Ollama 假响应 JSON
     * @return HTTP 客户端与请求 mock 组合
     */
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
