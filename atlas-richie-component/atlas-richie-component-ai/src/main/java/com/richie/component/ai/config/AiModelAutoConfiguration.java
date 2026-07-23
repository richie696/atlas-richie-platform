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
package com.richie.component.ai.config;

import com.richie.component.ai.config.chat.AiChatModel;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.richie.component.ai.api.voicechat.StsTicket;
import com.richie.component.ai.api.voicechat.VoiceChatModel;
import com.richie.component.ai.provider.dashscope.DashScopeQwenOmniVoiceChatModel;
import com.richie.component.ai.provider.doubao.DoubaoBidirectionTtsVoiceChatModel;
import com.richie.component.ai.provider.doubao.DoubaoStreamingAsrVoiceChatModel;
import com.richie.component.ai.provider.hunyuan.HunyuanStreamingAsrVoiceChatModel;
import com.richie.component.ai.provider.zhipu.ZhipuRealtimeVoiceChatModel;
import com.richie.component.ai.service.AiMultimodalService;
import com.richie.component.ai.service.VoiceChatService;
import com.richie.component.ai.service.impl.VoiceChatServiceImpl;
import com.richie.component.ai.service.VoiceStsService;
import com.richie.component.ai.service.impl.VoiceStsServiceImpl;
import com.richie.component.ai.service.impl.AiMultimodalServiceImpl;
import com.richie.component.ai.support.sign.AkSkHmacStsSigner;
import com.richie.component.ai.support.sign.AppCodeStsSigner;
import com.richie.component.ai.support.sign.BearerStsSigner;
import com.richie.component.ai.support.sign.StsSigner;
import com.richie.component.ai.support.sign.Tc3StsSigner;
import com.richie.component.ai.support.sign.XApiKeyStsSigner;
import com.richie.component.http.core.HttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.Map.Entry;

/**
 * AI模型自动配置类
 * 负责创建和管理各种AI模型的ChatClient实例
 *
 * @author richie696
 * @version 1.0
 * @since 2025-06-20 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.ai")
@EnableConfigurationProperties({AiModelProperties.class})
public class AiModelAutoConfiguration {

    /**
     * 构造器
     */
    public AiModelAutoConfiguration() {
    }

    /**
     * 创建AI模型ChatClient映射
     * 根据配置创建各种AI模型的ChatClient实例
     *
     * @param properties AI模型配置属性
     * @return 模型名称到ChatClient的映射
     */
    @Bean("aiChatClients")
    public Map<String, ChatClient> aiChatClients(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
        if (!properties.isConfigInitializationEnabled()) {
            log.info("AI组件已关闭配置文件初始化，等待运行时动态初始化模型");
            return Map.of();
        }
        Map<String, ChatClient> chatClients = aiChatClientFactory.createChatClients(properties);
        if (chatClients.isEmpty()) {
            log.warn("未配置任何AI模型");
        }
        return chatClients;
    }

    /**
     * 创建默认 EmbeddingModel（跟随默认模型策略：取配置中的首个模型）。
     *
     * <p>{@code @Primary} 仲裁：Spring AI starter（OpenAI / Ollama）会同时注册各自的
     * {@code EmbeddingModel} bean，业务侧依赖本中台组件时应由本 bean 胜出。
     * {@code @ConditionalOnMissingBean(EmbeddingModel.class)} 兜底：业务已自定义 EmbeddingModel 时不再覆盖。
     */
    @Bean("aiEmbeddingModel")
    @Primary
    @ConditionalOnMissingBean(EmbeddingModel.class)
    public EmbeddingModel aiEmbeddingModel(AiModelProperties properties, AiChatClientFactory aiChatClientFactory) {
        if (!properties.isConfigInitializationEnabled()) {
            log.info("AI组件未启用配置初始化，跳过默认EmbeddingModel自动创建，可在业务侧手工声明EmbeddingModel Bean");
            return null;
        }
        if (properties.getChat() == null || properties.getChat().isEmpty()) {
            log.warn("AI组件未配置任何模型，跳过默认EmbeddingModel自动创建，可在业务侧手工声明EmbeddingModel Bean");
            return null;
        }
        Entry<String, AiChatModel> defaultModelEntry = properties.getChat().entrySet().iterator().next();
        String defaultModelName = defaultModelEntry.getKey();
        log.info("初始化默认EmbeddingModel，跟随默认模型: {}", defaultModelName);
        return aiChatClientFactory.createEmbeddingModel(defaultModelName, defaultModelEntry.getValue());
    }

    /**
     * R-N：注册多模态服务（Rerank / Image / TTS / STT）。
     * <p>
     * 替代方案乙之前的 13 个分散的 {@code @Bean aiRerankModel / aiImageModel /
     * aiHunyuanTtsModel / ... / aiPanguSttModel} —— 现在由 {@link AiMultimodalServiceImpl}
     * 统一从 {@code platform.component.ai.{rerank|image|tts|stt}<key>=...} 加载，
     * 按 {@code vendor} 字段分派实现（{@code bailian} / {@code hunyuan} / {@code zhipu} /
     * {@code doubao} / {@code pangu}）。
     * <p>
     * 启动期显式调用 {@link AiMultimodalServiceImpl#refresh()} 一次，触发从
     * {@link AiModelProperties} 加载配置并 put 到内部 Map；后续业务可调用
     * {@code refresh()} 重新加载（如配置中心 / 数据库热更新场景）。
     * <p>
     * {@code HttpClient} 通过 {@link ObjectProvider} 注入，与 Chat 端
     * {@code AiChatServiceImpl} 一致 —— 未引入 http provider 的上下文能编译通过；
     * 运行期若需要多模态但 HttpClient 缺失，会由 {@code AiMultimodalServiceImpl}
     * 的 refresh() 阶段 fail-fast 并打 WARN 日志。
     */
    @Bean("aiMultimodalService")
    public AiMultimodalService aiMultimodalService(AiModelProperties properties,
                                                        ObjectProvider<HttpClient> httpClientProvider) {
        AiMultimodalServiceImpl service =
                new AiMultimodalServiceImpl(properties, httpClientProvider);
        if (properties.isConfigInitializationEnabled()) {
            service.refresh();
        } else {
            log.info("AI组件已关闭配置文件初始化，多模态模型等待运行时 refresh() 触发");
        }
        return service;
    }

    /**
     * Spring Cloud Config / Nacos Config / Apollo 等配置中心的事件桥接器。
     * <p>仅在 classpath 存在 {@code EnvironmentChangeEvent}（即业务方引入了
     * {@code spring-cloud-context}）时自动激活 —— 监听 {@code EnvironmentChangeEvent}，
     * 任一变更 key 以 {@code platform.component.ai.} 开头即触发多模态
     * {@link AiMultimodalServiceImpl#refresh()} 重建。设计镜像
     * {@code com.richie.component.web.core.reload.HotReloadCloudBridge}。
     * <p>未引入 spring-cloud 时本 Bean 不装配，多模态自动刷新降级为"启动期一次 +
     * 业务方手动 {@code refresh()}"语义，接口完全兼容。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(name = AiMultimodalCloudBridge.ENVIRONMENT_CHANGE_EVENT_CLASS)
    public AiMultimodalCloudBridge aiMultimodalCloudBridge(AiMultimodalService aiMultimodalService) {
        log.info("AI组件检测到 spring-cloud-context，启用多模态配置自动刷新桥接器 (EnvironmentChangeEvent → refresh())");
        return new AiMultimodalCloudBridge(aiMultimodalService);
    }

    // ============ R-N.4-alpha: WS + STS 凭证签发器自动装配 ============
    //
    // 凭证来源:复用 tts/stt 配置(同一 vendor、同一 long-term credential)。
    // 符合 R-N §14.4 原则 H(凭证复用),无需新增 voice-chat 配置节点。

    @Bean("aiZhipuStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.zhipu", name = "api-key")
    public StsSigner aiZhipuStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("zhipu");
        return new BearerStsSigner(StsTicket.VENDOR_ZHIPU, c.getApiKey(),
                c.getBaseUrl() != null ? c.getBaseUrl() : "https://open.bigmodel.cn/api/paas/v4/realtime");
    }

    @Bean("aiDashscopeStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.dashscope", name = "api-key")
    public StsSigner aiDashscopeStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("dashscope");
        return new BearerStsSigner(StsTicket.VENDOR_DASHSCOPE, c.getApiKey(),
                c.getBaseUrl() != null ? c.getBaseUrl() : "wss://dashscope.aliyuncs.com/api-ws/v1/realtime");
    }

    @Bean("aiHunyuanTokenHubStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.hunyuan", name = "api-key")
    public StsSigner aiHunyuanTokenHubStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("hunyuan");
        return new BearerStsSigner(StsTicket.VENDOR_HUNYUAN_TOKENHUB, c.getApiKey(),
                c.getBaseUrl() != null ? c.getBaseUrl() : "wss://hunyuan.tencent.com/v3/realtime");
    }

    @Bean("aiHunyuanTtsStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.hunyuan", name = "secret-id")
    public StsSigner aiHunyuanTtsStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("hunyuan");
        return new Tc3StsSigner(StsTicket.VENDOR_HUNYUAN_TTS, c.getSecretId(), c.getSecretKey(),
                c.getRegion() != null ? c.getRegion() : "ap-guangzhou",
                "tts",
                c.getEndpoint() != null ? c.getEndpoint() : "tts.tencentcloudapi.com");
    }

    @Bean("aiHunyuanSttStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.stt.hunyuan", name = "secret-id")
    public StsSigner aiHunyuanSttStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getStt().get("hunyuan");
        return new Tc3StsSigner(StsTicket.VENDOR_HUNYUAN_STT, c.getSecretId(), c.getSecretKey(),
                c.getRegion() != null ? c.getRegion() : "ap-guangzhou",
                "asr",
                c.getEndpoint() != null ? c.getEndpoint() : "asr.tencentcloudapi.com");
    }

    @Bean("aiPanguStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.pangu", name = "app-code")
    public StsSigner aiPanguStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("pangu");
        return new AppCodeStsSigner(StsTicket.VENDOR_PANGU, c.getAppCode(),
                c.getBaseUrl() != null ? c.getBaseUrl() : "https://pangu.apigw.com/v1/realtime");
    }

    @Bean("aiDoubaoOpenspeechStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "api-key")
    public StsSigner aiDoubaoOpenspeechStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("doubao");
        return new XApiKeyStsSigner(StsTicket.VENDOR_DOUBAO_OPENSPEECH, c.getApiKey(),
                c.getAppId(), c.getResourceId(),
                c.getBaseUrl() != null ? c.getBaseUrl() : "wss://openspeech.bytedance.com/api/v3/tts/bidirection");
    }

    @Bean("aiDoubaoVikingdbStsSigner")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "secret-key")
    public StsSigner aiDoubaoVikingdbStsSigner(AiModelProperties properties) {
        AbstractAudioModelConfig c = properties.getTts().get("doubao");
        return new AkSkHmacStsSigner(StsTicket.VENDOR_DOUBAO_VIKINGDB,
                c.getApiKey(),
                c.getSecretKey(),
                c.getRegion() != null ? c.getRegion() : "cn-north-1",
                "vikingdb",
                c.getBaseUrl() != null ? c.getBaseUrl() : "https://vikingdb.volcengineapi.com");
    }

    // ============ R-N.4-alpha: VoiceStsService 业务门面 ============
    //
    // 单一入口 — 业务代码 (BFF / Controller) 仅依赖 VoiceStsService 抽象,
    // 按 ctx.vendor + authDomain 自动分派到匹配的 StsSigner bean。

    @Bean("aiVoiceStsService")
    @ConditionalOnMissingBean
    public VoiceStsService aiVoiceStsService(java.util.List<StsSigner> signers) {
        return new VoiceStsServiceImpl(signers);
    }

    // ============ R-N.4-alpha: VoiceChatService 业务门面 ============

    @Bean("aiVoiceChatService")
    @ConditionalOnMissingBean
    public VoiceChatService aiVoiceChatService(java.util.List<VoiceChatModel> models) {
        return new VoiceChatServiceImpl(models);
    }

    // ============ R-N.4-beta: Zhipu Realtime WS 双工 VoiceChatModel ============

    @Bean("aiZhipuVoiceChatModel")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.zhipu", name = "api-key")
    public VoiceChatModel aiZhipuVoiceChatModel(VoiceStsService voiceStsService) {
        return new ZhipuRealtimeVoiceChatModel(voiceStsService);
    }

    // ============ R-N.4-gamma: 4 vendor WS 流式 VoiceChatModel ============

    @Bean("aiDashscopeVoiceChatModel")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.dashscope", name = "api-key")
    public VoiceChatModel aiDashscopeVoiceChatModel(VoiceStsService voiceStsService) {
        return new DashScopeQwenOmniVoiceChatModel(voiceStsService);
    }

    @Bean("aiDoubaoBidirectionTtsVoiceChatModel")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "app-id")
    public VoiceChatModel aiDoubaoBidirectionTtsVoiceChatModel(VoiceStsService voiceStsService) {
        return new DoubaoBidirectionTtsVoiceChatModel(voiceStsService);
    }

    @Bean("aiDoubaoStreamingAsrVoiceChatModel")
    @ConditionalOnProperty(prefix = "platform.component.ai.tts.doubao", name = "app-id")
    public VoiceChatModel aiDoubaoStreamingAsrVoiceChatModel(VoiceStsService voiceStsService) {
        return new DoubaoStreamingAsrVoiceChatModel(voiceStsService);
    }

    @Bean("aiHunyuanStreamingAsrVoiceChatModel")
    @ConditionalOnProperty(prefix = "platform.component.ai.stt.hunyuan", name = "secret-id")
    public VoiceChatModel aiHunyuanStreamingAsrVoiceChatModel(VoiceStsService voiceStsService) {
        return new HunyuanStreamingAsrVoiceChatModel(voiceStsService);
    }
}
