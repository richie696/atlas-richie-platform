package com.richie.component.ai.api.voicechat;

import java.io.Closeable;
import java.util.concurrent.Flow;

/**
 * 实时语音会话抽象 (双向 WebSocket 音频流)。
 *
 * <p>封装一次完整的实时语音交互生命周期:
 * <ol>
 *   <li>{@link #events()} — 订阅服务端事件流 (文本/音频/打断/工具调用等)</li>
 *   <li>{@link #sendAudio(VoiceChatEvent.AudioFrame)} — 客户端推送音频帧 (上行)</li>
 *   <li>{@link #sendText(String)} — 客户端推送文本 (用于打断后的文本输入)</li>
 *   <li>{@link #interrupt()} — 客户端主动打断服务端当前 TTS 输出</li>
 *   <li>{@link #close()} — 主动关闭会话 (实现可选择立即关闭或等待服务端确认)</li>
 * </ol>
 *
 * <h2>使用模式</h2>
 * <pre>{@code
 * try (VoiceConversation conv = voiceModel.open(VoiceChatConfig.builder().voice("zhitian_emo").build())) {
 *     conv.events().subscribe(event -> {
 *         switch (event.getType()) {
 *             case AUDIO_CHUNK -> audioPlayer.play(event.getAudio());
 *             case TRANSCRIPT_FINAL -> log.info("user said: {}", event.getText());
 *             case SESSION_END -> { /* ... *\/ }
 *         }
 *     });
 *     conv.sendAudio(microphone.read());
 * }
 * }</pre>
 *
 * <h2>实现约束</h2>
 * <ul>
 *   <li>必须线程安全 — {@link #events()} 订阅可能在任意线程, {@link #sendAudio} 也可能在任意线程</li>
 *   <li>{@link #close()} 幂等 — 多次调用效果等同于一次</li>
 *   <li>关闭后 {@link #events()} 应发出 {@link VoiceChatEvent.Type#SESSION_END} (best effort)</li>
 *   <li>不应在 {@link #sendAudio} 内部阻塞等待下游消费 (异步推送即可)</li>
 * </ul>
 *
 * @author Sisyphus
 * @see VoiceChatModel
 * @see VoiceChatEvent
 * @since 1.0.0
 */
public interface VoiceConversation extends Closeable {

    /**
     * 订阅事件流。
     *
     * <p>返回的发布者遵循 Reactive Streams 规范 ({@link Flow.Publisher}),
     * 业务侧可用任意 reactive 库消费 (Project Reactor / RxJava / JDK Flow)。
     *
     * <p>典型事件类型见 {@link VoiceChatEvent.Type}:
     * <ul>
     *   <li>{@link VoiceChatEvent.Type#TRANSCRIPT_PARTIAL} — ASR 增量识别结果</li>
     *   <li>{@link VoiceChatEvent.Type#TRANSCRIPT_FINAL} — ASR 最终识别结果</li>
     *   <li>{@link VoiceChatEvent.Type#AUDIO_CHUNK} — TTS 音频片段</li>
     *   <li>{@link VoiceChatEvent.Type#AUDIO_SENTENCE_END} — TTS 句子结束</li>
     *   <li>{@link VoiceChatEvent.Type#TOOL_CALL_REQUEST} — LLM 请求调用工具</li>
     *   <li>{@link VoiceChatEvent.Type#USER_INTERRUPTED} — 服务端检测到用户打断</li>
     *   <li>{@link VoiceChatEvent.Type#ERROR} — 会话内错误 (非致命)</li>
     *   <li>{@link VoiceChatEvent.Type#SESSION_END} — 会话结束 (致命)</li>
     * </ul>
     *
     * @return 事件发布者
     */
    Flow.Publisher<VoiceChatEvent> events();

    /**
     * 推送音频帧到服务端。
     *
     * <p>通常用于上行麦克风采样 (16kHz mono PCM 16-bit)。
     *
     * <p>实现应:
     * <ul>
     *   <li>非阻塞: 若发送队列满,可丢弃或报错 (由实现选择)</li>
     *   <li>线程安全: 任意线程调用</li>
     *   <li>会话关闭后调用应抛 {@link IllegalStateException}</li>
     * </ul>
     *
     * @param frame 音频帧 (不可空)
     * @throws IllegalStateException 会话已关闭或未开启
     * @throws NullPointerException  frame 为 null
     */
    void sendAudio(VoiceChatEvent.AudioFrame frame);

    /**
     * 推送文本到服务端 (用于打断后直接走 LLM 文本通路)。
     *
     * <p>仅部分 vendor 支持,不支持的 vendor 抛出 {@link UnsupportedOperationException}。
     *
     * @param text 文本内容 (不可空,长度 &gt; 0)
     * @throws UnsupportedOperationException 当前 vendor 不支持文本上行
     * @throws IllegalStateException        会话已关闭
     */
    void sendText(String text);

    /**
     * 主动打断服务端当前 TTS 输出。
     *
     * <p>对应 R-N §10 O4 客户端打断语义: 业务侧可在用户开始说话时调用此方法,
     * 让服务端立即停止 TTS,避免上下文干扰。
     *
     * @throws IllegalStateException 会话已关闭
     */
    void interrupt();

    /**
     * 会话是否处于活跃状态。
     *
     * @return true 表示尚未 {@link #close()}
     */
    boolean isActive();

    /**
     * 关闭会话。
     *
     * <p>幂等。关闭后所有 send* 方法抛 {@link IllegalStateException}。
     * 实现可选择立即关闭 (强制断 WS) 或优雅关闭 (发关闭帧后等待)。
     *
     * <p>实现规范: 应在内部资源释放前发出 {@link VoiceChatEvent.Type#SESSION_END} 事件。
     */
    @Override
    void close();
}