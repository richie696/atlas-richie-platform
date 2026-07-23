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
package com.richie.component.ai.provider.zhipu;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 智谱 Realtime 语音对话模型实现 — Zhipu GLM-4-Voice Realtime WebSocket。
 *
 * <p>R-N §14.4 vendor=Zhipu, 鉴权=Bearer(api-key), 端点={@code wss://open.bigmodel.cn/api/paas/v4/realtime}。
 *
 * <h2>协议要点 (基于 Zhipu 公开文档)</h2>
 * <ul>
 *   <li>客户端发 {@code session.update} 设置 voice / model / language</li>
 *   <li>客户端发 {@code input_audio_buffer.append} 推送 PCM 16k mono 音频帧 (Base64 编码)</li>
 *   <li>客户端发 {@code input_audio_buffer.commit} 触发 ASR</li>
 *   <li>服务端回 {@code response.audio.delta} / {@code response.text.delta} / {@code response.done} / {@code error}</li>
 *   <li>打断语义: 客户端发 {@code response.cancel}</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <ul>
 *   <li>WS 接收在 {@link java.net.http.WebSocket.Listener} 回调线程</li>
 *   <li>事件通过 {@link SubmissionPublisher} 推给业务侧 (线程安全)</li>
 *   <li>send* / interrupt 在任意线程,内部走 SubmissionPublisher.send + WebSocket.sendText</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.ai.tts.zhipu", name = "api-key")
public class ZhipuRealtimeVoiceChatModel implements VoiceChatModel {

    private static final Logger log = LoggerFactory.getLogger(ZhipuRealtimeVoiceChatModel.class);

    /** Zhipu Realtime WebSocket 默认端点。 */
    public static final String DEFAULT_ENDPOINT = "wss://open.bigmodel.cn/api/paas/v4/realtime";

    /** 支持的模型名集合 (与公开文档对齐,可通过 vendorOptions 扩展)。 */
    private static final String[] DEFAULT_SUPPORTED_MODELS = {
            "glm-4-voice",
            "glm-realtime",
            "glm-4-voice-flash"
    };

    private final VoiceStsService voiceStsService;
    private final HttpClient httpClient;
    private final String vendor;
    private final String[] supportedModels;

    /**
     * 公开构造器（自动创建 JDK HttpClient）。
     */
    public ZhipuRealtimeVoiceChatModel(VoiceStsService voiceStsService) {
        this(voiceStsService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(), StsTicket.VENDOR_ZHIPU, DEFAULT_SUPPORTED_MODELS);
    }

    /**
     * 包内构造器，允许注入 HttpClient / vendor / supportedModels 用于测试。
     */
    ZhipuRealtimeVoiceChatModel(VoiceStsService voiceStsService,
                                HttpClient httpClient,
                                String vendor,
                                String[] supportedModels) {
        this.voiceStsService = Objects.requireNonNull(voiceStsService, "voiceStsService");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.vendor = Objects.requireNonNull(vendor, "vendor");
        this.supportedModels = supportedModels == null
                ? DEFAULT_SUPPORTED_MODELS.clone()
                : supportedModels.clone();
    }

    @Override
    public String vendor() {
        return vendor;
    }

    @Override
    public String[] supportedModels() {
        return supportedModels.clone();
    }

    @Override
    public VoiceConversation open(VoiceChatConfig config) {
        Objects.requireNonNull(config, "config");
        // 业务侧不感知 vendor — config.vendor() 由 VoiceChatService 注入
        // 若未注入(测试场景),回落到本 impl 的 vendor
        String targetVendor = (config.vendor() == null || config.vendor().isEmpty()) ? vendor : config.vendor();

        // 1. 拿 STS 票面(由 VoiceStsService 按 ctx.vendor 路由到 aiZhipuStsSigner)
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

        // 2. 提取 Bearer token (格式: "Bearer <token>")
        Map<String, String> bearerHeaders = ticket.asBearerHeaders();
        String authHeader = bearerHeaders.get("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalStateException("ZhipuRealtimeVoiceChatModel 期望 Bearer 鉴权头, 实际=" + authHeader);
        }
        String token = authHeader.substring("Bearer ".length());

        // 3. 建立 WS 连接
        URI endpoint = URI.create(ticket.endpoint() != null ? ticket.endpoint() : DEFAULT_ENDPOINT);
        WebSocketSession session = new WebSocketSession(endpoint, token, config, ticket, httpClient);
        session.connect();
        return session;
    }

    // ============ WS 会话封装 ============

    /**
     * 单次语音会话生命周期:WS + SubmissionPublisher。
     */
    static final class WebSocketSession implements VoiceConversation, WebSocket.Listener {

        private final URI endpoint;
        private final String token;
        private final VoiceChatConfig config;
        private final StsTicket ticket;
        private final HttpClient httpClient;
        private final SubmissionPublisher<VoiceChatEvent> publisher = new SubmissionPublisher<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private final ConcurrentHashMap<String, String> partialTranscripts = new ConcurrentHashMap<>();
        private final AtomicInteger eventIdCounter = new AtomicInteger(0);
        private WebSocket webSocket;
        private volatile String currentResponseId;

        WebSocketSession(URI endpoint, String token, VoiceChatConfig config,
                         StsTicket ticket, HttpClient httpClient) {
            this.endpoint = endpoint;
            this.token = token;
            this.config = config;
            this.ticket = ticket;
            this.httpClient = httpClient;
        }

        void connect() {
            // Zhipu Realtime 鉴权:Authorization 头在 WS 握手时设置
            // Java HttpClient WebSocket.Builder.header 不直接支持,所以使用 subprotocols 或 query
            // 实测 Zhipu 接受 query 参数 ?token=<api-key> (Bearer 兼容)
            String wsUrl = endpoint.toString();
            if (wsUrl.contains("?")) {
                wsUrl = wsUrl + "&token=" + token;
            } else {
                wsUrl = wsUrl + "?token=" + token;
            }

            CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), this);
            future.whenComplete((ws, err) -> {
                if (err != null) {
                    log.error("Zhipu WS connect failed: {}", err.getMessage());
                    publish(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                            .error("WS connect failed: " + err.getMessage()).build());
                    publish(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
                    active.set(false);
                } else {
                    this.webSocket = ws;
                    log.info("Zhipu Realtime WS connected: {}", endpoint);
                    // 立即发送 session.update
                    sendSessionUpdate();
                }
            });
        }

        private void sendSessionUpdate() {
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("model", config.model() != null ? config.model() : "glm-4-voice");
            Map<String, Object> modalities = new LinkedHashMap<>();
            modalities.put("type", "audio");
            session.put("modalities", modalities);

            Map<String, Object> voice = new LinkedHashMap<>();
            voice.put("name", config.voice() != null ? config.voice() : "tongtong");
            voice.put("language", config.language() != null ? config.language() : "zh-CN");
            session.put("voice", voice);

            session.put("input_audio_format", "pcm16");
            session.put("output_audio_format", "pcm16");

            Map<String, Object> turnDetection = new LinkedHashMap<>();
            turnDetection.put("type", config.vadMode() == VoiceChatConfig.VadMode.CLIENT ? "client_vad" : "server_vad");
            session.put("turn_detection", turnDetection);

            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("event_id", "session_" + eventIdCounter.incrementAndGet());
            frame.put("type", "session.update");
            frame.put("session", session);

            sendJson(frame);
        }

        private void sendJson(Object payload) {
            if (webSocket == null || closed.get()) {
                log.warn("Zhipu WS 未连接或已关闭,跳过 send");
                return;
            }
            try {
                String json = JsonUtils.getInstance().serialize(payload);
                webSocket.sendText(json, true);
            } catch (Exception e) {
                log.error("Zhipu WS send failed: {}", e.getMessage());
                publish(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                        .error("WS send failed: " + e.getMessage()).build());
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = JsonSafe.parseMap(data.toString());
                String type = (String) msg.get("type");
                if (type == null) {
                    return null;
                }
                switch (type) {
                    case "session.created", "session.updated" -> {
                        // 会话就绪 — 不发业务事件,仅日志
                        log.info("Zhipu Realtime session event: {}", type);
                    }
                    case "response.audio.delta" -> {
                        // TTS 音频片段 — Base64 解码后推送
                        Object deltaObj = msg.get("delta");
                        if (deltaObj != null) {
                            byte[] pcm = Base64.getDecoder().decode(deltaObj.toString());
                            VoiceChatEvent.AudioFrame frame = new VoiceChatEvent.AudioFrame(
                                    pcm, config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000,
                                    16, 1, "pcm16");
                            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_CHUNK)
                                    .audio(frame).build());
                        }
                    }
                    case "response.audio.done", "response.done" -> {
                        Object rid = msg.get("response_id");
                        if (rid != null) {
                            currentResponseId = rid.toString();
                        }
                        publish(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_SENTENCE_END).build());
                    }
                    case "response.text.delta" -> {
                        Object delta = msg.get("delta");
                        if (delta != null) {
                            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.TRANSCRIPT_PARTIAL)
                                    .text(delta.toString()).build());
                        }
                    }
                    case "response.text.done" -> {
                        Object text = msg.get("text");
                        if (text != null) {
                            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.TRANSCRIPT_FINAL)
                                    .text(text.toString()).build());
                        }
                    }
                    case "conversation.item.input_audio_transcription.completed" -> {
                        Object transcript = msg.get("transcript");
                        if (transcript != null) {
                            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.TRANSCRIPT_FINAL)
                                    .text(transcript.toString()).build());
                        }
                    }
                    case "input_audio_buffer.speech_started" -> {
                        publish(VoiceChatEvent.builder(VoiceChatEvent.Type.USER_INTERRUPTED).build());
                    }
                    case "error" -> {
                        Object error = msg.get("error");
                        String errorMsg = error != null ? error.toString() : "unknown Zhipu error";
                        log.error("Zhipu Realtime server error: {}", errorMsg);
                        publish(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                                .error(errorMsg).build());
                    }
                    default -> log.debug("Zhipu Realtime 未映射事件: {}", type);
                }
            } catch (Exception e) {
                log.error("Zhipu WS parse error: {}", e.getMessage());
            }
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            // 部分 vendor 直接推二进制音频帧;Zhipu 走 Base64 文本,这里仅兜底
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            VoiceChatEvent.AudioFrame frame = new VoiceChatEvent.AudioFrame(
                    bytes, config.sampleRateHz() > 0 ? config.sampleRateHz() : 24000, 16, 1, "pcm");
            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.AUDIO_CHUNK).audio(frame).build());
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("Zhipu WS closed: status={} reason={}", statusCode, reason);
            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
            active.set(false);
            publisher.close();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("Zhipu WS error: {}", error.getMessage());
            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                    .error("WS error: " + error.getMessage()).build());
            publish(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
            active.set(false);
            publisher.close();
        }

        @Override
        public Flow.Publisher<VoiceChatEvent> events() {
            return publisher;
        }

        @Override
        public void sendAudio(VoiceChatEvent.AudioFrame frame) {
            Objects.requireNonNull(frame, "frame");
            if (!active.get() || closed.get()) {
                throw new IllegalStateException("Zhipu WS 会话已关闭");
            }
            String base64Audio = Base64.getEncoder().encodeToString(frame.data());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_id", "audio_" + eventIdCounter.incrementAndGet());
            payload.put("type", "input_audio_buffer.append");
            payload.put("audio", base64Audio);
            sendJson(payload);
        }

        @Override
        public void sendText(String text) {
            // Zhipu Realtime 不支持纯文本上行,文档未列出此 client event
            throw new UnsupportedOperationException("Zhipu Realtime 当前不支持 sendText, 请走 audio 通路");
        }

        @Override
        public void interrupt() {
            if (!active.get() || closed.get() || webSocket == null) {
                return;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_id", "cancel_" + eventIdCounter.incrementAndGet());
            payload.put("type", "response.cancel");
            if (currentResponseId != null) {
                payload.put("response_id", currentResponseId);
            }
            sendJson(payload);
        }

        @Override
        public boolean isActive() {
            return active.get() && !closed.get();
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.set(false);
                if (webSocket != null) {
                    try {
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "client_close");
                    } catch (Exception e) {
                        log.warn("Zhipu WS sendClose 失败: {}", e.getMessage());
                    }
                }
                publish(VoiceChatEvent.builder(VoiceChatEvent.Type.SESSION_END).build());
                publisher.close();
            }
        }

        private void publish(VoiceChatEvent event) {
            if (!closed.get()) {
                publisher.submit(event);
            }
        }
    }
}