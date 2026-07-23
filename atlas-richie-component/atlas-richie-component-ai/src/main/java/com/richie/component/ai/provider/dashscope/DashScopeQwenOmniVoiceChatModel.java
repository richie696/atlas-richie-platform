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
package com.richie.component.ai.provider.dashscope;

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
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 阿里云 DashScope qwen-omni Realtime 语音对话模型。
 *
 * <p>R-N §14.4 vendor=dashscope, 鉴权=Bearer(api-key), 端点={@code wss://dashscope.aliyuncs.com/api-ws/v1/realtime}。
 *
 * <h2>协议要点 (DashScope qwen-omni Realtime — OpenAI Realtime 兼容)</h2>
 * <ul>
 *   <li>客户端发 {@code session.update} 设置 voice / model / modalities</li>
 *   <li>客户端发 {@code input_audio_buffer.append} 推送 PCM 16k mono 音频帧 (Base64 编码)</li>
 *   <li>服务端回 {@code response.audio.delta} / {@code response.text.delta} / {@code response.done} / {@code error}</li>
 *   <li>打断语义: 客户端发 {@code response.cancel}</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.ai.tts.dashscope", name = "api-key")
public class DashScopeQwenOmniVoiceChatModel implements VoiceChatModel {

    private static final Logger log = LoggerFactory.getLogger(DashScopeQwenOmniVoiceChatModel.class);

    public static final String DEFAULT_ENDPOINT = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime";

    private static final String[] DEFAULT_SUPPORTED_MODELS = {
            "qwen-omni-turbo-realtime",
            "qwen-omni-realtime",
            "qwen-omni-turbo-realtime-latest"
    };

    private final VoiceStsService voiceStsService;
    private final HttpClient httpClient;
    private final String vendor;
    private final String[] supportedModels;

    public DashScopeQwenOmniVoiceChatModel(VoiceStsService voiceStsService) {
        this(voiceStsService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                StsTicket.VENDOR_DASHSCOPE, DEFAULT_SUPPORTED_MODELS);
    }

    DashScopeQwenOmniVoiceChatModel(VoiceStsService voiceStsService, HttpClient httpClient,
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
                        .authDomain("bearer")
                        .capability(StsTicket.CAPABILITY_VOICE_CHAT)
                        .model(config.model())
                        .ttlSeconds(3600)
                        .attributes(attrs)
                        .build();
        StsTicket ticket = voiceStsService.sign(ctx);
        String token = extractBearerToken(ticket);

        URI endpoint = URI.create(ticket.endpoint() != null ? ticket.endpoint() : DEFAULT_ENDPOINT);
        WebSocketSession session = new WebSocketSession(endpoint, token, config);
        session.connect();
        return session;
    }

    private String extractBearerToken(StsTicket ticket) {
        Map<String, String> h = ticket.asBearerHeaders();
        String a = h.get("Authorization");
        if (a == null || !a.startsWith("Bearer ")) {
            throw new IllegalStateException("DashScope 期望 Bearer 鉴权头, 实际=" + a);
        }
        return a.substring("Bearer ".length());
    }

    final class WebSocketSession implements VoiceConversation, WebSocket.Listener {
        private final URI endpoint;
        private final String token;
        private final VoiceChatConfig config;
        private final SubmissionPublisher<VoiceChatEvent> publisher = new SubmissionPublisher<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private WebSocket webSocket;

        WebSocketSession(URI endpoint, String token, VoiceChatConfig config) {
            this.endpoint = endpoint;
            this.token = token;
            this.config = config;
        }

        void connect() {
            String wsUrl = endpoint.toString() + (endpoint.toString().contains("?") ? "&" : "?") + "token=" + token;
            CompletableFuture<WebSocket> f = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), this);
            f.whenComplete((ws, err) -> {
                if (err != null) {
                    publishError("WS connect failed: " + err.getMessage());
                    endSession();
                } else {
                    this.webSocket = ws;
                    log.info("DashScope qwen-omni Realtime WS connected: {}", endpoint);
                    sendSessionUpdate();
                }
            });
        }

        private void sendSessionUpdate() {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("model", config.model() != null ? config.model() : "qwen-omni-turbo-realtime");
            session.put("modalities", new String[]{"text", "audio"});
            session.put("voice", config.voice() != null ? config.voice() : "Cherry");
            session.put("input_audio_format", "pcm16");
            session.put("output_audio_format", "pcm16");
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "session.update");
            frame.put("session", session);
            sendJson(frame);
        }

        private void sendJson(Object p) {
            if (webSocket == null || closed.get()) {
                return;
            }
            try {
                webSocket.sendText(JsonUtils.getInstance().serialize(p), true);
            } catch (Exception e) {
                log.error("DashScope WS send failed: {}", e.getMessage());
                publishError("WS send failed: " + e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = JsonSafe.parseMap(data.toString());
                String type = (String) msg.get("type");
                if (type == null) return null;
                switch (type) {
                    case "session.created", "session.updated" -> log.info("DashScope session event: {}", type);
                    case "response.audio.delta" -> {
                        Object d = msg.get("delta");
                        if (d != null) {
                            byte[] pcm = Base64.getDecoder().decode(d.toString());
                            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_CHUNK)
                                    .audio(new VoiceChatEvent.AudioFrame(
                                            pcm, config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000, 16, 1, "pcm16"))
                                    .build());
                        }
                    }
                    case "response.audio.done", "response.done" ->
                            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_SENTENCE_END).build());
                    case "response.text.delta" -> {
                        Object d = msg.get("delta");
                        if (d != null) {
                            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.TRANSCRIPT_PARTIAL)
                                    .text(d.toString()).build());
                        }
                    }
                    case "response.text.done", "conversation.item.input_audio_transcription.completed" -> {
                        Object t = msg.getOrDefault("text", msg.get("transcript"));
                        if (t != null) {
                            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.TRANSCRIPT_FINAL)
                                    .text(t.toString()).build());
                        }
                    }
                    case "input_audio_buffer.speech_started" ->
                            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.USER_INTERRUPTED).build());
                    case "error" -> {
                        Object e = msg.get("error");
                        publishError(e != null ? e.toString() : "unknown DashScope error");
                    }
                    default -> log.debug("DashScope unmapped event: {}", type);
                }
            } catch (Exception e) {
                log.error("DashScope WS parse error: {}", e.getMessage());
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int s, String r) {
            endSession();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable e) {
            publishError("WS error: " + e.getMessage());
            endSession();
        }

        @Override public java.util.concurrent.Flow.Publisher<VoiceChatEvent> events() { return publisher; }

        @Override
        public void sendAudio(VoiceChatEvent.AudioFrame frame) {
            Objects.requireNonNull(frame);
            if (!active.get()) throw new IllegalStateException("DashScope WS 会话已关闭");
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "input_audio_buffer.append");
            p.put("audio", Base64.getEncoder().encodeToString(frame.data()));
            sendJson(p);
        }

        @Override
        public void sendText(String text) {
            throw new UnsupportedOperationException("DashScope Realtime 当前不支持 sendText");
        }

        @Override
        public void interrupt() {
            if (!active.get() || webSocket == null) return;
            sendJson(Map.of("type", "response.cancel"));
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

        private void publishError(String err) {
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR).error(err).build());
        }

        private void endSession() {
            active.set(false);
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
            publisher.close();
        }
    }
}