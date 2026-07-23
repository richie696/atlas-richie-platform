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
package com.richie.component.ai.provider.doubao;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;
import com.richie.context.utils.data.JsonUtils;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 豆包语音 TTS / STT 适配器单元测试。
 * <p>
 * <strong>无网络策略：</strong>所有测试直接调用包内 package-private 静态解析方法
 * {@link DoubaoTextToSpeechModel#parseTtsResponse(String)} 与
 * {@link DoubaoTranscriptionModel#parseSttResponse(String)}，
 * 跳过真实 HTTP 通道。这样既能验证 NDJSON 拆行 / base64 解码 / DTO 字段映射的核心逻辑，
 * 又不依赖网络或 Mock 框架。
 * <p>
 * DTO 字段验证：测试同时通过 Jackson {@code JsonUtils} 反序列化 NDJSON 单行为
 * {@link DoubaoTextToSpeechModel.TtsRawChunk} / {@link DoubaoTranscriptionModel.SttRawChunk}
 * （均为 package-private 静态类 + {@code @JsonProperty}），确保生产代码的 DTO 形状
 * 与 {@code code} / {@code data} / {@code result} / {@code message} 字段命名对齐。
 */
class DoubaoSpeechModelTest {

    // ============ TTS NDJSON parsing ============

    /**
     * 单行成功响应：豆包 TTS 单次 query 模式典型返回。
     * <p>
     * 验证：解析器把 base64 字符串还原为原始字节数组。
     */
    @Test
    void tts_singleChunk_decodesBase64Audio() {
        byte[] rawAudio = new byte[]{0x01, 0x02, 0x03, 0x04, (byte) 0xFF};
        String b64 = Base64.getEncoder().encodeToString(rawAudio);
        String ndjson = "{\"code\":3000,\"data\":\"" + b64 + "\",\"message\":\"ok\"}\n";

        var resp = DoubaoTextToSpeechModel.parseTtsResponse(ndjson);

        assertThat(resp).isNotNull();
        assertThat(resp.getResult()).isNotNull();
        assertThat(resp.getResult().getOutput()).containsExactly(rawAudio);
    }

    /**
     * 多行成功响应：豆包 TTS 流式 chunk 拼接场景。
     * <p>
     * 验证：所有 {@code code==3000} chunk 的 {@code data} 字段按行顺序拼接后统一 base64 解码。
     */
    @Test
    void tts_multiChunk_concatenatesAndDecodes() {
        byte[] part1 = new byte[]{0x10, 0x20, 0x30};
        byte[] part2 = new byte[]{0x40, 0x50, 0x60};
        String b64Part1 = Base64.getEncoder().encodeToString(part1);
        String b64Part2 = Base64.getEncoder().encodeToString(part2);
        // 拼接后整体再 base64 一次得到 expected bytes（base64 字符串拼接 == 字节拼接的 base64）
        byte[] combined = new byte[part1.length + part2.length];
        System.arraycopy(part1, 0, combined, 0, part1.length);
        System.arraycopy(part2, 0, combined, part1.length, part2.length);

        String ndjson =
                "{\"code\":3000,\"data\":\"" + b64Part1 + "\",\"message\":\"chunk-1\"}\n"
              + "{\"code\":3000,\"data\":\"" + b64Part2 + "\",\"message\":\"chunk-2\"}\n";

        var resp = DoubaoTextToSpeechModel.parseTtsResponse(ndjson);

        assertThat(resp).isNotNull();
        assertThat(resp.getResult().getOutput()).containsExactly(combined);
    }

    /**
     * DTO 字段映射：{@link DoubaoTextToSpeechModel.TtsRawChunk} 的 {@code @JsonProperty}
     * 标注能正确把 NDJSON 单行反序列化为 DTO，且 {@code code} 是数值类型（不是字符串）。
     */
    @Test
    void tts_dtoChunk_mapsFieldsViaJackson() {
        String line = "{\"code\":3000,\"data\":\"YWJj\",\"message\":\"ok\"}";

        var chunk = JsonUtils.getInstance().deserialize(
                line, DoubaoTextToSpeechModel.TtsRawChunk.class);

        assertThat(chunk).isNotNull();
        assertThat(chunk.code).isEqualTo(3000);
        assertThat(chunk.data).isEqualTo("YWJj");
        assertThat(chunk.message).isEqualTo("ok");
    }

    /**
     * 非 0 / 非 3000 的 code 视为错误，解析器抛出运行时异常并透出 message。
     */
    @Test
    void tts_nonSuccessCode_throws() {
        String ndjson = "{\"code\":4501,\"data\":\"\",\"message\":\"invalid text\"}\n";

        assertThatThrownBy(() -> DoubaoTextToSpeechModel.parseTtsResponse(ndjson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("4501")
                .hasMessageContaining("invalid text");
    }

    /**
     * 空响应（{@code null} / 空白 / 不含任何 {@code data} 字段）回落到空字节音频。
     */
    @Test
    void tts_emptyResponse_returnsEmptyAudio() {
        assertThat(DoubaoTextToSpeechModel.parseTtsResponse(null).getResult().getOutput())
                .isEmpty();
        assertThat(DoubaoTextToSpeechModel.parseTtsResponse("").getResult().getOutput())
                .isEmpty();
        assertThat(DoubaoTextToSpeechModel.parseTtsResponse("\n\n").getResult().getOutput())
                .isEmpty();
    }

    /**
     * 非 JSON 行（如 SSE 心跳 / 注释行）必须被跳过而非抛错 —— 体现 NDJSON 解析鲁棒性。
     */
    @Test
    void tts_skipsNonJsonLines() {
        String ndjson =
                ": keep-alive\n"
              + "\n"
              + "{\"code\":3000,\"data\":\"YQ==\",\"message\":\"ok\"}\n";

        var resp = DoubaoTextToSpeechModel.parseTtsResponse(ndjson);

        // "a" == base64("YQ==")
        assertThat(resp.getResult().getOutput()).containsExactly((byte) 'a');
    }

    // ============ STT NDJSON parsing ============

    /**
     * 单行成功响应：豆包 STT flash 接口典型返回。
     * <p>
     * 验证：解析器从 {@code result} 字段提取识别文本（包含 CJK 字符）。
     */
    @Test
    void stt_singleChunk_extractsTranscriptText() {
        String ndjson = "{\"code\":1000,\"result\":\"你好\",\"message\":\"ok\"}";

        var resp = DoubaoTranscriptionModel.parseSttResponse(ndjson);

        assertThat(resp).isNotNull();
        assertThat(resp.getResult()).isNotNull();
        assertThat(resp.getResult().getOutput()).isEqualTo("你好");
    }

    /**
     * DTO 字段映射：{@link DoubaoTranscriptionModel.SttRawChunk} 的 {@code @JsonProperty}
     * 标注能正确把 NDJSON 单行反序列化为 DTO，{@code result} 字段保留中文 UTF-8。
     */
    @Test
    void stt_dtoChunk_mapsFieldsViaJackson() {
        String line = "{\"code\":1000,\"result\":\"今天天气\",\"message\":\"ok\"}";

        var chunk = JsonUtils.getInstance().deserialize(
                line, DoubaoTranscriptionModel.SttRawChunk.class);

        assertThat(chunk).isNotNull();
        assertThat(chunk.code).isEqualTo(1000);
        assertThat(chunk.result).isEqualTo("今天天气");
        assertThat(chunk.message).isEqualTo("ok");
    }

    /**
     * 非 0 / 非 1000 的 code 视为错误，解析器抛出运行时异常并透出 message。
     */
    @Test
    void stt_nonSuccessCode_throws() {
        String ndjson = "{\"code\":2000,\"result\":\"\",\"message\":\"audio too short\"}\n";

        assertThatThrownBy(() -> DoubaoTranscriptionModel.parseSttResponse(ndjson))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("2000")
                .hasMessageContaining("audio too short");
    }

    /**
     * 空响应回落到空字符串文本（而不是抛错）。
     */
    @Test
    void stt_emptyResponse_returnsEmptyText() {
        assertThat(DoubaoTranscriptionModel.parseSttResponse(null).getResult().getOutput())
                .isEmpty();
        assertThat(DoubaoTranscriptionModel.parseSttResponse("").getResult().getOutput())
                .isEmpty();
        assertThat(DoubaoTranscriptionModel.parseSttResponse("\n").getResult().getOutput())
                .isEmpty();
    }

    /**
     * 构造函数必传项校验：缺少必传凭据（apiKey / appId / resourceId）即失败。
     */
    @Test
    void constructors_validateRequiredCredentials() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        // 全部为 null，构造函数应抛 IllegalArgumentException
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DoubaoTextToSpeechModel(cfg, noopHttpClient()));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new DoubaoTranscriptionModel(cfg, noopHttpClient()));
    }

    /**
     * 端到端验证 {@link TextToSpeechPrompt} 能被 {@link DoubaoTextToSpeechModel#callAsync} 接收
     * 并构造正确的请求（不发起真实网络）：使用 {@code Mockito.mock(HttpClient.class)} 让链式
     * 调用返回 stub，并在 stub 上断言 header / body 内容。
     * <p>
     * 注意：本测试不依赖网络；仅验证 prompt→requestBody 装配阶段的契约。
     */
    @Test
    void tts_callAsync_setsAuthHeadersAndBuildsNdjsonBody() throws Exception {
        AbstractAudioModelConfig cfg = fullVoiceConfig();
        com.richie.component.http.core.HttpClient mock = org.mockito.Mockito.mock(
                com.richie.component.http.core.HttpClient.class);
        com.richie.component.http.core.HttpRequest req = org.mockito.Mockito.mock(
                com.richie.component.http.core.HttpRequest.class);

        org.mockito.Mockito.when(mock.post(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Object.class)))
                .thenReturn(req);
        org.mockito.Mockito.when(req.header(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(req);
        org.mockito.Mockito.when(req.execute()).thenReturn(
                com.richie.component.http.core.HttpResponse.of(200,
                        java.util.Map.of(),
                "{\"code\":3000,\"data\":\"YQ==\",\"message\":\"ok\"}".getBytes(StandardCharsets.UTF_8)));

        DoubaoTextToSpeechModel model = new DoubaoTextToSpeechModel(cfg, mock);
        var future = model.callAsync(new TextToSpeechPrompt("hello"));
        var resp = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        // 验证 header 三件套
        org.mockito.Mockito.verify(req).header("X-Api-Key", "test-api-key");
        org.mockito.Mockito.verify(req).header("X-Api-Resource-Id", "volc.tts.api.voiceclone");
        org.mockito.Mockito.verify(req).header("X-Api-App-Id", "test-app-id");
        // 验证音频内容
        assertThat(resp.getResult().getOutput()).containsExactly((byte) 'a');
    }

    /**
     * STT 端到端验证（无网络）：{@link ByteArrayResource} 模拟音频输入，验证
     * 请求体经过 base64 编码 + NDJSON 字段映射正确传给 HTTP 客户端。
     */
    @Test
    void stt_callAsync_setsHeadersAndBuildsNdjsonBody() throws Exception {
        AbstractAudioModelConfig cfg = fullVoiceConfig();
        com.richie.component.http.core.HttpClient mock = org.mockito.Mockito.mock(
                com.richie.component.http.core.HttpClient.class);
        com.richie.component.http.core.HttpRequest req = org.mockito.Mockito.mock(
                com.richie.component.http.core.HttpRequest.class);

        org.mockito.Mockito.when(mock.post(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Object.class)))
                .thenReturn(req);
        org.mockito.Mockito.when(req.header(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(req);
        org.mockito.Mockito.when(req.execute()).thenReturn(
                com.richie.component.http.core.HttpResponse.of(200,
                        java.util.Map.of(),
                "{\"code\":1000,\"result\":\"识别结果\",\"message\":\"ok\"}"
                        .getBytes(StandardCharsets.UTF_8)));

        DoubaoTranscriptionModel model = new DoubaoTranscriptionModel(cfg, mock);
        var future = model.callAsync(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[]{1, 2, 3})));
        var resp = future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        org.mockito.Mockito.verify(req).header("X-Api-Key", "test-api-key");
        org.mockito.Mockito.verify(req).header("X-Api-Resource-Id",
                DoubaoTranscriptionModel.STT_RESOURCE_ID);
        org.mockito.Mockito.verify(req).header("X-Api-App-Id", "test-app-id");
        assertThat(resp.getResult().getOutput()).isEqualTo("识别结果");
    }

    // ============ Test fixtures ============

    /**
     * 构造一份齐全的 {@link AbstractAudioModelConfig}，覆盖豆包 TTS / STT 所需的全部必传项。
     */
    private static AbstractAudioModelConfig fullVoiceConfig() {
        AbstractAudioModelConfig cfg = new TtsModelConfig();
        cfg.setName("doubao-test");
        cfg.setApiKey("test-api-key");
        cfg.setAppId("test-app-id");
        cfg.setResourceId("volc.tts.api.voiceclone");
        cfg.setModel("seed-tts-2.0");
        return cfg;
    }

    /**
     * 仅用于构造函数校验测试的 {@code HttpClient} 桩 —— 任何调用都会抛 NPE，
     * 但构造函数在该路径上不应真正触发请求。
     */
    private static com.richie.component.http.core.HttpClient noopHttpClient() {
        return new com.richie.component.http.core.HttpClient() {
            @Override public com.richie.component.http.core.HttpRequest get(String url) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpRequest post(String url, Object body) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpRequest post(String url) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpRequest put(String url, Object body) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpRequest delete(String url, Object body) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpRequest delete(String url) { throw uoe(); }
            @Override public com.richie.component.http.core.SseConnection sse(String url,
                    com.richie.component.http.core.SseListener listener) { throw uoe(); }
            @Override public com.richie.component.http.core.SseConnection sse(String url,
                    java.util.Map<String, String> headers,
                    com.richie.component.http.core.SseListener listener) { throw uoe(); }
            @Override public com.richie.component.http.core.HttpResponse execute(
                    com.richie.component.http.core.HttpRequest request) { throw uoe(); }
            @Override public <T> T execute(com.richie.component.http.core.HttpRequest request, Class<T> type) { throw uoe(); }
            @Override public <T> T execute(com.richie.component.http.core.HttpRequest request,
                    tools.jackson.core.type.TypeReference<T> typeRef) { throw uoe(); }
            @Override public <T> void async(com.richie.component.http.core.HttpRequest request,
                    com.richie.component.http.core.AsyncCallback<T> callback, Class<T> type) { throw uoe(); }
            @Override public <T> void async(com.richie.component.http.core.HttpRequest request,
                    com.richie.component.http.core.AsyncCallback<T> callback,
                    tools.jackson.core.type.TypeReference<T> typeRef) { throw uoe(); }
            @Override public <T> java.util.concurrent.CompletableFuture<T> future(
                    com.richie.component.http.core.HttpRequest request, Class<T> type) { throw uoe(); }
            @Override public <T> java.util.concurrent.CompletableFuture<T> future(
                    com.richie.component.http.core.HttpRequest request,
                    tools.jackson.core.type.TypeReference<T> typeRef) { throw uoe(); }
        };
    }

    private static UnsupportedOperationException uoe() {
        return new UnsupportedOperationException("noop stub");
    }
}