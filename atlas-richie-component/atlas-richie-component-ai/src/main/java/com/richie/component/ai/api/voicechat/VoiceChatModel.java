package com.richie.component.ai.api.voicechat;

/**
 * 实时语音对话模型 SPI 接口。
 *
 * <p>本接口遵循 R-N 设计 §14.3.4 原则 J — WS + STS 统一接口原则:
 * 业务代码 (BFF / Controller) 仅依赖 {@code VoiceChatModel} 抽象,配置文件切换 vendor 时业务代码零改动。
 *
 * <h2>使用模式</h2>
 * <pre>{@code
 * // 业务代码 (BFF)
 * public class BffVoiceController {
 *     private final VoiceChatService voiceChatService;
 *
 *     public void handleVoiceCall(String userId) {
 *         VoiceChatConfig config = VoiceChatConfig.builder()
 *             .model("glm-4-voice")
 *             .voice("tongtong")
 *             .language("zh-CN")
 *             .build();
 *
 *         try (VoiceConversation conv = voiceChatService.open(config)) {
 *             conv.events().subscribe(this::handleEvent);
 *             // ... 推流 + 业务处理
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>实现模式</h2>
 * 每个 vendor 对应一个独立实现类,全部实现为 Spring @Component / @Service:
 * <ul>
 *   <li>{@code ZhipuRealtimeVoiceChatModel} — 智谱 glm-4-voice Realtime WS</li>
 *   <li>{@code DashScopeQwenOmniVoiceChatModel} — 阿里 DashScope qwen-omni Realtime WS</li>
 *   <li>{@code DoubaoBidirectionTtsVoiceChatModel} — 字节豆包 bidirectional TTS WS</li>
 *   <li>{@code DoubaoStreamingAsrVoiceChatModel} — 字节豆包 streaming ASR WS</li>
 *   <li>{@code HunyuanStreamingAsrVoiceChatModel} — 腾讯云 TC3 streaming ASR WS</li>
 * </ul>
 *
 * <h2>配置驱动</h2>
 * vendor 由 {@link #vendor()} 返回,业务侧通过 {@code voiceChatService} 按配置路由。
 * 不允许业务代码出现 {@code if (vendor == "zhipu")} 之类的分支。
 *
 * @author Sisyphus
 * @see VoiceConversation
 * @see VoiceChatConfig
 * @see VoiceChatEvent
 * @since 1.0.0
 */
public interface VoiceChatModel {

    /**
     * 当前实现所属厂商标识。
     *
     * <p>取值见 {@code StsTicket#VENDOR_*} 系列常量。
     *
     * @return 厂商标识字符串
     */
    String vendor();

    /**
     * 当前实现支持的模型名集合。
     *
     * <p>用于 {@code VoiceChatService} 路由前的快速校验 (避免向不支持的 vendor 请求错误模型)。
     *
     * @return 支持的模型名数组 (不可变, 按配置顺序)
     */
    String[] supportedModels();

    /**
     * 判断当前实现是否能处理给定配置。
     *
     * <p>默认规则: {@link #vendor()} 等于 {@code config.getVendor()}
     * 且 {@link #supportedModels()} 包含 {@code config.getModel()}。
     *
     * @param config 配置
     * @return true 表示能处理
     */
    default boolean supports(VoiceChatConfig config) {
        if (config == null || config.vendor() == null || config.vendor().isEmpty()
                || config.model() == null) {
            return false;
        }
        if (!vendor().equals(config.vendor())) {
            return false;
        }
        for (String m : supportedModels()) {
            if (m.equals(config.model())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 打开一个实时语音会话。
     *
     * <p>实现必须:
     * <ul>
     *   <li>在调用 {@code VoiceChatService} 之前,已通过 {@code com.richie.component.ai.service.VoiceStsService} 拿到 {@code StsTicket}</li>
     *   <li>建立 WebSocket 连接 (使用 ticket 的 endpoint + credentials)</li>
     *   <li>立即发送会话初始化协议 (例如 Zhipu Realtime 的 session.update 帧)</li>
     *   <li>返回的 {@link VoiceConversation} 已可订阅 {@link VoiceConversation#events()}</li>
     * </ul>
     *
     * <p>失败时抛 {@link IllegalStateException} (凭证缺失 / 协议失败 / 网络失败)。
     *
     * @param config 会话配置
     * @return 双工会话
     */
    VoiceConversation open(VoiceChatConfig config);
}