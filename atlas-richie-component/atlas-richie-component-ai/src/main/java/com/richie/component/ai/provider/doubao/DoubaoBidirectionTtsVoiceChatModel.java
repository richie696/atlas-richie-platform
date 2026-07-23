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

import com.richie.component.ai.provider.support.JsonSafe;

import com.richie.component.ai.support.sign.VendorStsContext;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatEvent;
import com.richie.component.ai.api.voicechat.VoiceChatModel;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceStsService;
import com.richie.context.utils.data.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 字节豆包 Doubao 双向 TTS (Bidirectional TTS v3) 语音对话模型。
 *
 * <p>R-N §14.4 vendor=doubao-openspeech, 鉴权=X-Api-Key, 端点={@code wss://openspeech.bytedance.com/api/v3/tts/bidirection}。
 *
 * <h2>协议要点 (Doubao Bidirectional TTS v3)</h2>
 * <ul>
 *   <li>WS 握手时客户端带 header: {@code Authorization=Bearer <appid>}; 后续上行按段发 JSON 文本帧 (text in)</li>
 *   <li>服务端下行混合: JSON 文本帧 (event/sentence boundaries) + 二进制帧 (audio PCM)</li>
 *   <li>上行 JSON 帧: {@code {"user":{"uid":"..."}, "namespace":"BidirectionalTTS", "req_params":{"text":"...", "speaker":"...", "audio_params":{...}}}}</li>
 *   <li>打断语义: 发 finish=true 触发服务端 end-of-segment</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "api-key")
public class DoubaoBidirectionTtsVoiceChatModel implements VoiceChatModel {

    private static final Logger log = LoggerFactory.getLogger(DoubaoBidirectionTtsVoiceChatModel.class);

    public static final String DEFAULT_ENDPOINT = "wss://openspeech.bytedance.com/api/v3/tts/bidirection";

    private static final String[] DEFAULT_SUPPORTED_MODELS = {
            "seed-tts-2.0",
            "doubao-tts",
            "volcano-tts"
    };

    private final VoiceStsService voiceStsService;
    private final HttpClient httpClient;
    private final String vendor;
    private final String[] supportedModels;

    public DoubaoBidirectionTtsVoiceChatModel(VoiceStsService voiceStsService) {
        this(voiceStsService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                StsTicket.VENDOR_DOUBAO_OPENSPEECH, DEFAULT_SUPPORTED_MODELS);
    }

    DoubaoBidirectionTtsVoiceChatModel(VoiceStsService voiceStsService, HttpClient httpClient,
                                       String vendor, String[] supportedModels) {
        this.voiceStsService = Objects.requireNonNull(voiceStsService);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vendor = Objects.requireNonNull(vendor);
        this.supportedModels = supportedModels == null ? DEFAULT_SUPPORTED_MODELS.clone() : supportedModels.clone();
    }

    @Override public String vendor() { return vendor; }
    @Override public String[] supportedModels() { return supportedModels.clone(); }

    @Override
    public VoiceConversation open(VoiceChatConfig config) {
        Objects.requireNonNull(config);
        String targetVendor = (config.vendor() == null || config.vendor().isEmpty()) ? vendor : config.vendor();

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("endpoint", DEFAULT_ENDPOINT);
        VendorStsContext ctx =
                VendorStsContext.builder()
                        .vendor(targetVendor)
                        .authDomain("x-api-key")
                        .capability(StsTicket.CAPABILITY_TTS_STREAM)
                        .model(config.model())
                        .ttlSeconds(3600)
                        .attributes(attrs)
                        .build();
        StsTicket ticket = voiceStsService.sign(ctx);

        URI endpoint = URI.create(ticket.endpoint() != null ? ticket.endpoint() : DEFAULT_ENDPOINT);
        return new DoubaoSession(endpoint, ticket, config, httpClient);
    }

    static final class DoubaoSession implements VoiceConversation, WebSocket.Listener {
        private final URI endpoint;
        private final StsTicket ticket;
        private final VoiceChatConfig config;
        private final HttpClient httpClient;
        private final String sessionId = UUID.randomUUID().toString();
        private final SubmissionPublisher<VoiceChatEvent> publisher = new SubmissionPublisher<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private WebSocket webSocket;

        DoubaoSession(URI endpoint, StsTicket ticket, VoiceChatConfig config, HttpClient httpClient) {
            this.endpoint = endpoint;
            this.ticket = ticket;
            this.config = config;
            this.httpClient = httpClient;
        }

        @Override
        public void onOpen(WebSocket ws) {
            log.info("Doubao TTS WS opened: {}", endpoint);
            this.webSocket = ws;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence text, boolean last) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = JsonSafe.parseMap(text.toString());
                // Doubao 下行 JSON 通常是 {code, message, data: {audio: base64, ...}}
                Object code = msg.get("code");
                if (code != null && !code.toString().equals("0") && !code.toString().equals("200")) {
                    publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                            .error("Doubao TTS error code=" + code + " msg=" + msg.get("message")).build());
                } else if (msg.containsKey("data")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payload = (Map<String, Object>) msg.get("data");
                    if (payload != null && payload.get("audio") != null) {
                        byte[] audio = Base64.getDecoder().decode(payload.get("audio").toString());
                        publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_CHUNK)
                                .audio(new VoiceChatEvent.AudioFrame(
                                        audio, config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000, 16, 1, "pcm"))
                                .build());
                    }
                    if (Boolean.TRUE.equals(payload.get("is_final")) || msg.containsKey("sentence_end")) {
                        publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_SENTENCE_END).build());
                    }
                }
            } catch (Exception e) {
                log.error("Doubao TTS parse error: {}", e.getMessage());
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_CHUNK)
                    .audio(new VoiceChatEvent.AudioFrame(
                            bytes, config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000, 16, 1, "pcm"))
                    .build());
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int s, String r) {
            endSession();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable e) {
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR).error(e.getMessage()).build());
            endSession();
        }

        @Override public java.util.concurrent.Flow.Publisher<VoiceChatEvent> events() { return publisher; }

        @Override
        public void sendAudio(VoiceChatEvent.AudioFrame frame) {
            throw new UnsupportedOperationException("Doubao 双向 TTS 模式不支持 sendAudio, 请用 sendText");
        }

        @Override
        public void sendText(String text) {
            Objects.requireNonNull(text);
            if (!active.get() || closed.get() || webSocket == null) {
                throw new IllegalStateException("Doubao TTS WS 未连接或已关闭");
            }
            Map<String, Object> audioParams = new LinkedHashMap<>();
            audioParams.put("format", "pcm");
            audioParams.put("sample_rate", config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000);
            audioParams.put("bits", 16);
            audioParams.put("channel", 1);

            Map<String, Object> reqParams = new LinkedHashMap<>();
            reqParams.put("text", text);
            reqParams.put("speaker", config.voice() != null ? config.voice() : "zh_female_shuangkuai");
            reqParams.put("audio_params", audioParams);

            Map<String, Object> user = new LinkedHashMap<>();
            user.put("uid", sessionId);

            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("user", user);
            frame.put("namespace", "BidirectionalTTS");
            frame.put("req_params", reqParams);
            try {
                webSocket.sendText(JsonUtils.getInstance().serialize(frame), true);
            } catch (Exception e) {
                throw new IllegalStateException("Doubao TTS WS sendText failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void interrupt() {
            if (!active.get() || webSocket == null) return;
            // 打断:发 finish=true 段结束
            Map<String, Object> reqParams = new LinkedHashMap<>();
            reqParams.put("text", "");
            reqParams.put("finish", true);
            Map<String, Object> user = new LinkedHashMap<>();
            user.put("uid", sessionId);
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("user", user);
            frame.put("namespace", "BidirectionalTTS");
            frame.put("req_params", reqParams);
            try {
                webSocket.sendText(JsonUtils.getInstance().serialize(frame), true);
            } catch (Exception e) {
                log.warn("Doubao TTS interrupt failed: {}", e.getMessage());
            }
        }

        @Override public boolean isActive() { return active.get() && !closed.get(); }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.set(false);
                if (webSocket != null) {
                    try { webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client_close"); } catch (Exception ignored) {}
                }
                publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
                publisher.close();
            }
        }

        private void endSession() {
            active.set(false);
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
            publisher.close();
        }
    }
}