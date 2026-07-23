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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 字节豆包 Doubao 流式 ASR (Streaming ASR v2) 语音识别模型。
 *
 * <p>R-N §14.4 vendor=doubao-openspeech, 鉴权=X-Api-Key, 端点={@code wss://openspeech.bytedance.com/api/v2/asr}。
 *
 * <h2>协议要点 (Doubao Streaming ASR v2)</h2>
 * <ul>
 *   <li>客户端发 JSON 帧 full client request 初始化 + 二进制音频帧 (PCM)</li>
 *   <li>服务端回 JSON 帧: partial/final transcript + error</li>
 *   <li>上行音频流: 二进制 PCM 16k mono</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "api-key")
public class DoubaoStreamingAsrVoiceChatModel implements VoiceChatModel {

    private static final Logger log = LoggerFactory.getLogger(DoubaoStreamingAsrVoiceChatModel.class);

    public static final String DEFAULT_ENDPOINT = "wss://openspeech.bytedance.com/api/v2/asr";

    private static final String[] DEFAULT_SUPPORTED_MODELS = {
            "doubao-streaming-asr",
            "asr-2.0",
            "bigmodel-asr"
    };

    private final VoiceStsService voiceStsService;
    private final HttpClient httpClient;
    private final String vendor;
    private final String[] supportedModels;

    public DoubaoStreamingAsrVoiceChatModel(VoiceStsService voiceStsService) {
        this(voiceStsService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                StsTicket.VENDOR_DOUBAO_OPENSPEECH, DEFAULT_SUPPORTED_MODELS);
    }

    DoubaoStreamingAsrVoiceChatModel(VoiceStsService voiceStsService, HttpClient httpClient,
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
                        .capability(StsTicket.CAPABILITY_STT_STREAM)
                        .model(config.model())
                        .ttlSeconds(3600)
                        .attributes(attrs)
                        .build();
        StsTicket ticket = voiceStsService.sign(ctx);

        URI endpoint = URI.create(ticket.endpoint() != null ? ticket.endpoint() : DEFAULT_ENDPOINT);
        DoubaoAsrSession session = new DoubaoAsrSession(endpoint, ticket, config, httpClient);
        session.connect();
        return session;
    }

    final class DoubaoAsrSession implements VoiceConversation, WebSocket.Listener {
        private final URI endpoint;
        private final StsTicket ticket;
        private final VoiceChatConfig config;
        private final HttpClient httpClient;
        private final SubmissionPublisher<VoiceChatEvent> publisher = new SubmissionPublisher<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private WebSocket webSocket;

        DoubaoAsrSession(URI endpoint, StsTicket ticket, VoiceChatConfig config, HttpClient httpClient) {
            this.endpoint = endpoint;
            this.ticket = ticket;
            this.config = config;
            this.httpClient = httpClient;
        }

        void connect() {
            // Doubao ASR v2 鉴权通过 query 参数 (since SDK 限制)
            Map<String, String> doubaoHeaders = ticket.asHeaderMap();
            String appId = doubaoHeaders.get("X-Api-App-Id");
            String token = doubaoHeaders.get("X-Api-Access-Key");
            String wsUrl = endpoint.toString()
                    + (endpoint.toString().contains("?") ? "&" : "?")
                    + "appid=" + (appId != null ? appId : "")
                    + "&token=" + (token != null ? token : "")
                    + "&cluster=" + (config.model() != null ? config.model() : DEFAULT_SUPPORTED_MODELS[0]);

            CompletableFuture<WebSocket> f = httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(wsUrl), this);
            f.whenComplete((ws, err) -> {
                if (err != null) {
                    publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                            .error("WS connect failed: " + err.getMessage()).build());
                    endSession();
                } else {
                    this.webSocket = ws;
                    log.info("Doubao ASR WS connected: {}", endpoint);
                    sendFullClientRequest();
                }
            });
        }

        private void sendFullClientRequest() {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("app", Map.of("cluster", config.model() != null ? config.model() : DEFAULT_SUPPORTED_MODELS[0]));
            req.put("user", Map.of("uid", "rn-rtc-" + System.currentTimeMillis()));
            req.put("audio", Map.of(
                    "format", "pcm",
                    "rate", config.sampleRateHz() > 0 ? config.sampleRateHz() : 16000,
                    "bits", 16,
                    "channel", 1));
            req.put("request", Map.of(
                    "reqid", System.currentTimeMillis() + "-" + Math.random(),
                    "nbest", 1,
                    "result_type", "full",
                    "language", config.language() != null ? config.language() : "zh-CN"));
            try {
                webSocket.sendText(JsonUtils.getInstance().serialize(req), true);
            } catch (Exception e) {
                log.error("Doubao ASR full request failed: {}", e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = JsonSafe.parseMap(data.toString());
                Object code = msg.get("code");
                if (code != null && !code.toString().equals("0") && !code.toString().equals("200")) {
                    publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                            .error("Doubao ASR code=" + code + " msg=" + msg.get("message")).build());
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) msg.get("result");
                if (result == null) return null;
                @SuppressWarnings("unchecked")
                java.util.List<Object> texts = (java.util.List<Object>) result.get("text");
                if (texts == null || texts.isEmpty()) return null;
                String text = texts.get(0).toString();
                Boolean isFinal = (Boolean) result.get("is_final");
                publisher.submit(VoiceChatEvent.builder(
                                isFinal != null && isFinal ? VoiceChatEvent.Type.TRANSCRIPT_FINAL
                                        : VoiceChatEvent.Type.TRANSCRIPT_PARTIAL)
                        .text(text).build());
            } catch (Exception e) {
                log.error("Doubao ASR parse error: {}", e.getMessage());
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
            publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR).error(e.getMessage()).build());
            endSession();
        }

        @Override public java.util.concurrent.Flow.Publisher<VoiceChatEvent> events() { return publisher; }

        @Override
        public void sendAudio(VoiceChatEvent.AudioFrame frame) {
            Objects.requireNonNull(frame);
            if (!active.get() || closed.get() || webSocket == null) {
                throw new IllegalStateException("Doubao ASR WS 未连接或已关闭");
            }
            try {
                webSocket.sendBinary(ByteBuffer.wrap(frame.data()), true);
            } catch (Exception e) {
                throw new IllegalStateException("Doubao ASR WS sendBinary failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void sendText(String text) {
            throw new UnsupportedOperationException("Doubao ASR 不支持 sendText, 请用 sendAudio");
        }

        @Override public void interrupt() {
            // ASR 不需要打断语义
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