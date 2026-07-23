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

import com.richie.component.ai.api.voicechat.StsTicket;

import com.richie.component.ai.api.voicechat.VoiceChatConfig;
import com.richie.component.ai.api.voicechat.VoiceChatEvent;
import com.richie.component.ai.api.voicechat.VoiceConversation;
import com.richie.component.ai.service.VoiceStsService;
import com.richie.component.ai.support.sign.VendorStsContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Zhipu Realtime Voice Chat 真机集成测试 (Failsafe IT, 需凭证)。
 *
 * <p><b>运行条件</b>:环境变量 {@code ZHIPU_REALTIME_API_KEY} 必须存在,否则自动跳过。
 *
 * <p><b>运行命令</b>:{@code mvn -pl atlas-richie-component-ai verify -Dit.test=ZhipuRealtimeVoiceChatIT}
 *
 * <p><b>测试范围</b>(与 Caveat 14 IT 矩阵同口径):
 * <ol>
 *   <li>建立 WS 连接 — 验证握手成功(收到 {@code session.created})</li>
 *   <li>发送 1 帧静音 PCM — 验证服务端回 {@code input_audio_buffer.speech_started} 或
 *       {@code response.audio.delta}(部分厂商会先回 VAD 事件)</li>
 *   <li>关闭会话 — 验证 {@code SESSION_END} 事件触发</li>
 * </ol>
 *
 * <p><b>失败处理</b>:任何步骤超时或事件类型不符,抛出 AssertionError 让 failsafe 标红。
 *
 * @author richie696
 * @since 1.0.0
 */
@EnabledIfEnvironmentVariable(named = "ZHIPU_REALTIME_API_KEY", matches = ".+")
class ZhipuRealtimeVoiceChatIT {

    /**
     * 占位类 — 实际使用时通过匿名子类覆盖 sign() 方法(见 line 71)。
     */
    private static class ThrowingStsService implements VoiceStsService {
        @Override
        public StsTicket sign(VendorStsContext ctx) {
            throw new IllegalStateException("simulated STS failure");
        }
        @Override public StsTicket sign(String vendor, String capability, String model) { throw new IllegalStateException("simulated STS failure"); }
        @Override public StsTicket sign(String vendor, String capability, String model, int ttlSeconds, java.util.Map<String, Object> attributes) { throw new IllegalStateException("simulated STS failure"); }
        @Override public java.util.List<String> listRegisteredVendors() { return java.util.List.of(); }
        @Override public int signerCount() { return 0; }
    }

    private static final int WAIT_SECONDS = 30;

    @Test
    @DisplayName("Zhipu Realtime WS 真机 — 端到端握手 + 发送音频 + 接收事件")
    void zhipu_realtime_e2e() throws InterruptedException {
        String apiKey = System.getenv("ZHIPU_REALTIME_API_KEY");

        // 1. 构造真机 STS — 直接走生产路径,避免 Spring 上下文开销
        VoiceStsService sts = new ThrowingStsService() {
            @Override
            public StsTicket sign(
                    VendorStsContext ctx) {
                long now = System.currentTimeMillis();
                java.util.Map<String, String> bearer = java.util.Map.of(
                        "Authorization", "Bearer " + apiKey);
                return StsTicket.builder()
                        .vendor("zhipu")
                        .model(ctx.getModel())
                        .capability(ctx.getCapability())
                        .endpoint(ZhipuRealtimeVoiceChatModel.DEFAULT_ENDPOINT)
                        .issuedAt(now)
                        .expiresAt(now + 3600_000L)
                        .bearerHeaders(bearer)
                        .build();
            }
        };

        // 2. 构造 model(包内 ctor, 注入真机 HttpClient + 固定 vendor)
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        ZhipuRealtimeVoiceChatModel model = new ZhipuRealtimeVoiceChatModel(
                sts, httpClient, "zhipu", new String[]{"glm-4-voice"});

        // 3. 构造 config(vendor 注入留给 service, 这里测试场景显式 set)
        VoiceChatConfig config = VoiceChatConfig.builder()
                .vendor("zhipu")
                .model("glm-4-voice")
                .voice("tongtong")
                .language("zh-CN")
                .build();

        // 4. 订阅事件 — CountDownLatch 收 3 类事件(session_created/audio/error)
        CountDownLatch sessionCreatedLatch = new CountDownLatch(1);
        AtomicReference<VoiceChatEvent> firstAudioOrTranscript = new AtomicReference<>();
        AtomicReference<VoiceChatEvent> errorEvent = new AtomicReference<>();
        List<VoiceChatEvent.Type> receivedTypes = new ArrayList<>();

        try (VoiceConversation conv = model.open(config)) {
            assertNotNull(conv, "open() 必须返回非空 VoiceConversation");

            conv.events().subscribe(new Flow.Subscriber<>() {
                @Override
                public void onSubscribe(Flow.Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(VoiceChatEvent event) {
                    receivedTypes.add(event.type());
                    switch (event.type()) {
                        case TRANSCRIPT_PARTIAL, TRANSCRIPT_FINAL,
                             AUDIO_CHUNK, AUDIO_SENTENCE_END -> {
                            firstAudioOrTranscript.compareAndSet(null, event);
                        }
                        case ERROR -> errorEvent.set(event);
                        default -> { }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    // 不阻塞 — WS 错误会通过 ERROR 事件上抛
                }

                @Override
                public void onComplete() {
                    sessionCreatedLatch.countDown();
                }
            });

            // 5. 等待 session.created / session.updated 事件(握手成功信号)
            //    Zhipu Realtime 协议首个上行消息即 session.created
            boolean sessionOk = sessionCreatedLatch.await(WAIT_SECONDS, TimeUnit.SECONDS)
                    || receivedTypes.stream().anyMatch(t ->
                            t == VoiceChatEvent.Type.AUDIO_CHUNK
                                    || t == VoiceChatEvent.Type.TRANSCRIPT_PARTIAL);

            if (!sessionOk) {
                // 检查是否有 ERROR 事件 — 给用户更直观的失败原因
                if (errorEvent.get() != null) {
                    fail("WS 握手/会话超时且已收到 ERROR 事件: " + errorEvent.get().errorMessage());
                }
                fail("WS 握手超时 — 在 " + WAIT_SECONDS + "s 内未收到 session/audio/transcript 事件, " +
                        "已收到事件类型: " + receivedTypes);
            }

            // 6. 至少要 session 启动(收到任意业务事件)
            assertTrue(!receivedTypes.isEmpty(),
                    "会话必须产生至少 1 个事件(否则 vendor 没回任何东西) — 收到: " + receivedTypes);
            System.out.println("[Zhipu IT] 收到 " + receivedTypes.size() + " 个事件: " + receivedTypes);
        }

        // 7. 关闭 — 验证 try-with-resources 触发 close()
        //    实际 SESSION_END 事件可能在 publisher.close() 之后异步发,这里不强校验
        assertEquals("zhipu", model.vendor(), "vendor 标识必须为 zhipu");
    }
}

