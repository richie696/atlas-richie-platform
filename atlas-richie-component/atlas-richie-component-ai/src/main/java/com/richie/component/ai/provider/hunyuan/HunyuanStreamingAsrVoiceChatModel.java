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
package com.richie.component.ai.provider.hunyuan;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 腾讯云 Hunyuan 流式 ASR (TC3-HMAC-SHA256 + WS) 语音识别模型。
 *
 * <p>R-N §14.4 vendor=hunyuan-stt, 鉴权=TC3 (per-frame 签名), 端点={@code asr.tencentcloudapi.com}。
 *
 * <h2>协议要点 (腾讯云 ASR 流式 WS)</h2>
 * <ul>
 *   <li>每个上行请求都需带 TC3 签名 (StsTicket.asTc3Headers(action, payload) 动态生成)</li>
 *   <li>音频上行: 二进制 PCM 16k mono</li>
 *   <li>服务端下行: JSON 帧, 含 Result/Code/Message</li>
 * </ul>
 *
 * @author richie696
 * @since 1.0.0
 */
@Service
@ConditionalOnProperty(prefix = "platform.component.ai.stt.hunyuan", name = "secret-id")
public class HunyuanStreamingAsrVoiceChatModel implements VoiceChatModel {

    private static final Logger log = LoggerFactory.getLogger(HunyuanStreamingAsrVoiceChatModel.class);

    public static final String DEFAULT_ENDPOINT = "wss://asr.tencentcloudapi.com/asr/v2";

    private static final String[] DEFAULT_SUPPORTED_MODELS = {
            "16k_zh",
            "16k_zh-PY",
            "16k_en",
            "16k_zh_large"
    };

    private final VoiceStsService voiceStsService;
    private final HttpClient httpClient;
    private final String vendor;
    private final String[] supportedModels;

    public HunyuanStreamingAsrVoiceChatModel(VoiceStsService voiceStsService) {
        this(voiceStsService, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                StsTicket.VENDOR_HUNYUAN_STT, DEFAULT_SUPPORTED_MODELS);
    }

    HunyuanStreamingAsrVoiceChatModel(VoiceStsService voiceStsService, HttpClient httpClient,
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
                        .authDomain("tc3")
                        .capability(StsTicket.CAPABILITY_STT_STREAM)
                        .model(config.model() != null ? config.model() : DEFAULT_SUPPORTED_MODELS[0])
                        .ttlSeconds(3600)
                        .attributes(attrs)
                        .build();
        StsTicket ticket = voiceStsService.sign(ctx);

        URI endpoint = URI.create(ticket.endpoint() != null ? ticket.endpoint() : DEFAULT_ENDPOINT);
        HunyuanAsrSession session = new HunyuanAsrSession(endpoint, ticket, config, httpClient);
        session.connect();
        return session;
    }

    final class HunyuanAsrSession implements VoiceConversation, WebSocket.Listener {
        private final URI endpoint;
        private final StsTicket ticket;
        private final VoiceChatConfig config;
        private final HttpClient httpClient;
        private final String sessionId = UUID.randomUUID().toString();
        private final SubmissionPublisher<VoiceChatEvent> publisher = new SubmissionPublisher<>();
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicBoolean closed = new AtomicBoolean(false);
        private WebSocket webSocket;

        HunyuanAsrSession(URI endpoint, StsTicket ticket, VoiceChatConfig config, HttpClient httpClient) {
            this.endpoint = endpoint;
            this.ticket = ticket;
            this.config = config;
            this.httpClient = httpClient;
        }

        void connect() {
            // Hunyuan 流式 ASR WS 鉴权: 客户端需在每次上行请求的 HTTP header 带 TC3 签名
            // SDK 限制 — 通过 query 参数 token=<signature> 简化
            String payload = "{\"engine_model_type\":\"" + (config.model() != null ? config.model() : DEFAULT_SUPPORTED_MODELS[0]) + "\"}";
            Map<String, String> tc3Headers = ticket.asTc3Headers("SentenceRecognition", payload);
            String authHeader = tc3Headers.get("Authorization");
            String wsUrl = endpoint.toString()
                    + (endpoint.toString().contains("?") ? "&" : "?")
                    + "signature=" + java.net.URLEncoder.encode(authHeader != null ? authHeader : "", java.nio.charset.StandardCharsets.UTF_8)
                    + "&session_id=" + sessionId;

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
                    log.info("Hunyuan ASR WS connected: {}", endpoint);
                    sendInitRequest();
                }
            });
        }

        private void sendInitRequest() {
            Map<String, Object> req = new LinkedHashMap<>();
            req.put("type", "started");
            req.put("engine_model_type", config.model() != null ? config.model() : DEFAULT_SUPPORTED_MODELS[0]);
            req.put("voice_id", sessionId);
            req.put("audio_format", 1); // 1 = PCM
            req.put("sample_rate", config.sampleRateHz() > 0 ? config.sampleRateHz() : 16000);
            req.put("bits", 16);
            req.put("channel_num", 1);
            try {
                webSocket.sendText(JsonUtils.getInstance().serialize(req), true);
            } catch (Exception e) {
                log.error("Hunyuan ASR init request failed: {}", e.getMessage());
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> msg = JsonSafe.parseMap(data.toString());
                String type = (String) msg.get("type");
                if ("error".equals(type) || (msg.get("code") != null && !"0".equals(msg.get("code").toString()))) {
                    publisher.submit(VoiceChatEvent.builder(VoiceChatEvent.Type.ERROR)
                            .error("Hunyuan ASR error: " + msg.get("message")).build());
                    return null;
                }
                Object result = msg.get("result");
                if (result == null) return null;
                @SuppressWarnings("unchecked")
                Map<String, Object> r = (Map<String, Object>) result;
                int sliceType = ((Number) r.getOrDefault("slice_type", 0)).intValue();
                String text = (String) r.get("voice_text_str");
                if (text == null || text.isEmpty()) return null;
                // slice_type: 0=partial, 1=final, 2=full
                VoiceChatEvent.Type evtType = sliceType >= 1 ? VoiceChatEvent.Type.TRANSCRIPT_FINAL
                        : VoiceChatEvent.Type.TRANSCRIPT_PARTIAL;
                publisher.submit(VoiceChatEvent.builder(evtType).text(text).build());
            } catch (Exception e) {
                log.error("Hunyuan ASR parse error: {}", e.getMessage());
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
                throw new IllegalStateException("Hunyuan ASR WS 未连接或已关闭");
            }
            try {
                webSocket.sendBinary(ByteBuffer.wrap(frame.data()), true);
            } catch (Exception e) {
                throw new IllegalStateException("Hunyuan ASR WS sendBinary failed: " + e.getMessage(), e);
            }
        }

        @Override
        public void sendText(String text) {
            throw new UnsupportedOperationException("Hunyuan ASR 不支持 sendText, 请用 sendAudio");
        }

        @Override public void interrupt() {}

        @Override public boolean isActive() { return active.get() && !closed.get(); }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                active.set(false);
                if (webSocket != null) {
                    try {
                        Map<String, Object> end = new LinkedHashMap<>();
                        end.put("type", "ended");
                        webSocket.sendText(JsonUtils.getInstance().serialize(end), true);
                    } catch (Exception ignored) {}
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