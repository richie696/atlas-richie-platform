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
package com.richie.component.ai.provider.bailian;

import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BailianImageEmbeddingAdapter} 的纯单元测试（hermetic —— 不发起真实 DashScope 调用）。
 *
 * <p>策略：mock {@link HttpClient} + {@link HttpRequest},把 {@code request.execute(Class)}
 * 桩成返回预设 {@link BailianImageEmbeddingAdapter.EmbeddingRawResponse}。覆盖：
 * <ul>
 *   <li>{@link BailianImageEmbeddingAdapter#dimensions()} 常量短路 —— 不发请求</li>
 *   <li>{@link BailianImageEmbeddingAdapter#call(EmbeddingRequest)} 文本分支正确解析</li>
 *   <li>{@link BailianImageEmbeddingAdapter#embedImage(String)} 图像分支正确解析</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
class BailianImageEmbeddingAdapterTest {

    @Test
    void dimensions_returnsConstantWithoutCallingHttp() {
        HttpClient httpClient = mock(HttpClient.class);
        BailianImageEmbeddingAdapter adapter = new BailianImageEmbeddingAdapter(
                httpClient, "sk-test", null, null);

        int dims = adapter.dimensions();

        assertThat(dims).isEqualTo(1024);
        assertThat(dims).isEqualTo(BailianImageEmbeddingAdapter.DIMENSIONS);
        verify(httpClient, never()).post(anyString(), any());
        verify(httpClient, never()).post(anyString());
    }

    @Test
    void embed_singleText_parsesEmbeddingResponse() {
        String json = """
                {
                  "output": {
                    "embeddings": [
                      {"embedding": [0.0123, -0.0456, 0.0789], "text_index": 0}
                    ]
                  }
                }
                """;
        RequestFixture fx = fixture(json);

        BailianImageEmbeddingAdapter adapter = new BailianImageEmbeddingAdapter(
                fx.httpClient, "sk-test", "https://example.com/mm", null);

        float[] vec = adapter.embed("hello");

        assertThat(vec).isNotNull();
        // mock 返回的向量只放了 3 个值 — 实际长度由 vendor 返回值决定(不强制 1024)
        assertThat(vec.length).isEqualTo(3);
        assertThat(vec[0]).isEqualTo(0.0123f, within(1e-6f));
        assertThat(vec[1]).isEqualTo(-0.0456f, within(1e-6f));
        assertThat(vec[2]).isEqualTo(0.0789f, within(1e-6f));
    }

    @Test
    void call_multipleInputs_returnsOneEmbeddingPerInput() {
        String json = """
                {
                  "output": {
                    "embeddings": [
                      {"embedding": [0.1, 0.2, 0.3], "text_index": 0},
                      {"embedding": [0.4, 0.5, 0.6], "text_index": 1}
                    ]
                  }
                }
                """;
        RequestFixture fx = fixture(json);

        BailianImageEmbeddingAdapter adapter = new BailianImageEmbeddingAdapter(
                fx.httpClient, "sk-test", null, "multimodal-embedding-v1");

        EmbeddingRequest request = new EmbeddingRequest(List.of("a", "b"), null);
        EmbeddingResponse resp = adapter.call(request);

        assertThat(resp).isNotNull();
        assertThat(resp.getResults()).hasSize(2);
        assertThat(resp.getResults().get(0).getOutput()[0]).isEqualTo(0.1f, within(1e-6f));
        assertThat(resp.getResults().get(1).getOutput()[1]).isEqualTo(0.5f, within(1e-6f));
    }

    @Test
    void embedImage_base64Input_callsDashScopeImageEndpoint() {
        String json = """
                {
                  "output": {
                    "embeddings": [
                      {"embedding": [0.99, 0.88, 0.77], "image_index": 0}
                    ]
                  }
                }
                """;
        RequestFixture fx = fixture(json);

        BailianImageEmbeddingAdapter adapter = new BailianImageEmbeddingAdapter(
                fx.httpClient, "sk-test", null, null);

        float[] vec = adapter.embedImage("data:image/jpeg;base64,/9j/AAAA");

        assertThat(vec).isNotNull();
        assertThat(vec[0]).isEqualTo(0.99f, within(1e-6f));
        assertThat(vec[1]).isEqualTo(0.88f, within(1e-6f));
        assertThat(vec[2]).isEqualTo(0.77f, within(1e-6f));
    }

    @Test
    void constructor_blankBaseUrl_defaultsToDashScopeMultimodalEndpoint() throws Exception {
        BailianImageEmbeddingAdapter adapter = new BailianImageEmbeddingAdapter(
                mock(HttpClient.class), "sk-test", null, null);

        Field baseUrl = BailianImageEmbeddingAdapter.class.getDeclaredField("baseUrl");
        baseUrl.setAccessible(true);
        Field model = BailianImageEmbeddingAdapter.class.getDeclaredField("defaultModel");
        model.setAccessible(true);

        assertThat(baseUrl.get(adapter)).isEqualTo(BailianImageEmbeddingAdapter.DEFAULT_BASE_URL);
        assertThat(model.get(adapter)).isEqualTo(BailianImageEmbeddingAdapter.DEFAULT_MODEL);
    }

    // -------- helpers --------

    /** HTTP 桩组合:把假 JSON 反序列化为 {@link BailianImageEmbeddingAdapter.EmbeddingRawResponse}。 */
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