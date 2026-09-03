package cn.richie696.component.ai.provider.volcengine;

import cn.richie696.component.http.core.HttpClient;
import cn.richie696.component.http.core.HttpRequest;
import cn.richie696.component.http.core.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class VolcengineEmbeddingAdapterTest {

    @Test
    void call_usesDedicatedEndpointAndMergesModelParameters() {
        HttpClient client = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        HttpResponse response = mock(HttpResponse.class);
        when(client.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.execute()).thenReturn(response);
        when(response.bodyAsString()).thenReturn("{\"data\":[{\"embedding\":[0.1,0.2]}]}");

        VolcengineEmbeddingAdapter adapter = new VolcengineEmbeddingAdapter(
                client,
                "ark-test",
                "https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal",
                "doubao-embedding-vision-241215",
                Map.of("dimensions", 1024));

        float[] vector = adapter.embed("hello");

        assertThat(vector).containsExactly(0.1f, 0.2f);
        verify(client).post(
                eq("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal"),
                eq(Map.of(
                        "dimensions", 1024,
                        "model", "doubao-embedding-vision-241215",
                        "input", List.of(Map.of("type", "text", "text", "hello"))))
        );
    }
}
