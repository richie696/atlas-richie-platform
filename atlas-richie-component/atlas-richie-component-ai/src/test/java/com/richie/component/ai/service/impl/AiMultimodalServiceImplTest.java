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
package com.richie.component.ai.service.impl;

import com.richie.component.ai.config.multimodal.audio.AbstractAudioModelConfig;

import com.richie.component.ai.config.multimodal.stt.SttProvider;

import com.richie.component.ai.config.multimodal.tts.TtsProvider;

import com.richie.component.ai.config.multimodal.stt.SttModelConfig;

import com.richie.component.ai.config.multimodal.rerank.RerankProvider;

import com.richie.component.ai.config.multimodal.tts.TtsModelConfig;

import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingProvider;
import com.richie.component.ai.config.multimodal.image.ImageModelConfig;

import com.richie.component.ai.config.multimodal.image.ImageProvider;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.api.image.ImageEmbeddingModel;
import com.richie.component.ai.config.AiModelProperties;
import com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter;
import com.richie.component.ai.provider.bailian.BailianRerankModel;
import com.richie.component.ai.provider.doubao.DoubaoTextToSpeechModel;
import com.richie.component.ai.provider.doubao.DoubaoTranscriptionModel;
import com.richie.component.ai.provider.hunyuan.HunyuanTextToSpeechModel;
import com.richie.component.ai.provider.pangu.PanguRerankModel;
import com.richie.component.ai.provider.pangu.PanguTranscriptionModel;
import com.richie.component.ai.provider.zhipu.ZhipuRerankModel;
import com.richie.component.ai.provider.zhipu.ZhipuTranscriptionModel;
import com.richie.component.http.core.HttpClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AiMultimodalServiceImpl} 的纯单元测试。
 * <p>
 * 不依赖 Spring 上下文：不打 {@code @SpringBootTest}，仅用 Mockito 构造 {@link ObjectProvider}
 * 来控制 HttpClient 是否可用；vendor 类在自身构造期只存字段不发请求（请求侧由集成测试覆盖）。
 * <p>
 * 覆盖目标：
 * <ul>
 *   <li>refresh() 空 Map → 全部维度返回 0，getter 全部 null</li>
 *   <li>refresh() 注入 4 个维度全厂商配置 → 4 个 Map 均填充对应实现类</li>
 *   <li>未识别 vendor 走"按条目跳过"语义（不抛、不阻断其他）</li>
 *   <li>HttpClient 缺失时 4 个维度全部跳过并 WARN</li>
 *   <li>同名覆盖（cover-replace）语义</li>
 *   <li>keySet 暴露的 {@code Set} 不可变</li>
 * </ul>
 */
class AiMultimodalServiceImplTest {

    private AiModelProperties properties;
    private HttpClient mockHttpClient;
    private AiMultimodalServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new AiModelProperties();
        mockHttpClient = mock(HttpClient.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<HttpClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mockHttpClient);

        service = new AiMultimodalServiceImpl(properties, provider);
    }

    @Test
    void refresh_withEmptyMaps_registersNothing() {
        service.refresh();

        assertThat(service.getRerankModelNames()).isEmpty();
        assertThat(service.getImageModelNames()).isEmpty();
        assertThat(service.getImageEmbeddingModelNames()).isEmpty();
        assertThat(service.getTextToSpeechModelNames()).isEmpty();
        assertThat(service.getTranscriptionModelNames()).isEmpty();
        assertThat(service.getRerankModel("anything")).isNull();
        assertThat(service.getImageModel("anything")).isNull();
        assertThat(service.getImageEmbeddingModel("anything")).isNull();
        assertThat(service.getTextToSpeechModel("anything")).isNull();
        assertThat(service.getTranscriptionModel("anything")).isNull();
    }

    @Test
    void refresh_withBailianRerankAndImage_registersCorrectInstances() {
        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        RerankModelConfig rerankCfg = new RerankModelConfig();
        rerankCfg.setProvider(RerankProvider.BAILIAN);
        rerankCfg.setApiKey("sk-rerank-test");
        rerankCfg.setBaseUrl(null);
        rerankCfg.setModel("gte-rerank");
        rerank.put("default", rerankCfg);

        Map<String, ImageModelConfig> image = new LinkedHashMap<>();
        ImageModelConfig imgCfg = new ImageModelConfig();
        imgCfg.setProvider(ImageProvider.BAILIAN);
        imgCfg.setApiKey("sk-image-test");
        imgCfg.setBaseUrl(null);
        imgCfg.setModel("wanx-v1");
        image.put("img", imgCfg);

        properties.setRerank(rerank);
        properties.setImage(image);

        service.refresh();

        RerankModel rerankModel = service.getRerankModel("default");
        assertThat(rerankModel).isNotNull().isInstanceOf(BailianRerankModel.class);

        ImageModel imageModel = service.getImageModel("img");
        assertThat(imageModel).isNotNull();
        assertThat(imageModel.getClass().getSimpleName()).isEqualTo("BailianImageAdapter");
    }

    @Test
    void refresh_withAllThreeRerankVendors_registersCorrectInstances() {
        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        rerank.put("bailian-rerank", makeBailianCfg("bailian"));
        rerank.put("zhipu-rerank", makeZhipuCfg("zhipu"));
        rerank.put("pangu-rerank", makePanguCfg("pangu"));

        properties.setRerank(rerank);

        service.refresh();

        assertThat(service.getRerankModel("bailian-rerank")).isInstanceOf(BailianRerankModel.class);
        assertThat(service.getRerankModel("zhipu-rerank")).isInstanceOf(ZhipuRerankModel.class);
        assertThat(service.getRerankModel("pangu-rerank")).isInstanceOf(PanguRerankModel.class);
        assertThat(service.getRerankModelNames()).containsExactly(
                "bailian-rerank", "zhipu-rerank", "pangu-rerank");
    }

    @Test
    void refresh_withHunyuanDoubaoTts_registersVendorSpecificInstances() {
        Map<String, TtsModelConfig> tts = new LinkedHashMap<>();
        tts.put("hunyuan-tts", makeTtsCfg("hunyuan", false));
        tts.put("doubao-tts", makeTtsCfg("doubao", false));

        properties.setTts(tts);

        service.refresh();

        TextToSpeechModel hunyuanInstance = service.getTextToSpeechModel("hunyuan-tts");
        assertThat(hunyuanInstance).isInstanceOf(HunyuanTextToSpeechModel.class);

        TextToSpeechModel doubaoInstance = service.getTextToSpeechModel("doubao-tts");
        assertThat(doubaoInstance).isInstanceOf(DoubaoTextToSpeechModel.class);
    }

    @Test
    void refresh_withAllFourSttVendors_registersCorrectInstances() {
        Map<String, SttModelConfig> stt = new LinkedHashMap<>();
        stt.put("hunyuan-stt", makeSttCfg("hunyuan", false));
        stt.put("zhipu-stt", makeSttCfg("zhipu", true));
        stt.put("doubao-stt", makeSttCfg("doubao", false));
        stt.put("pangu-stt", makeSttCfg("pangu", false));

        properties.setStt(stt);

        service.refresh();

        assertThat(service.getTranscriptionModel("hunyuan-stt").getClass().getSimpleName())
                .isEqualTo("HunyuanTranscriptionModel");
        assertThat(service.getTranscriptionModel("zhipu-stt")).isInstanceOf(ZhipuTranscriptionModel.class);
        assertThat(service.getTranscriptionModel("doubao-stt")).isInstanceOf(DoubaoTranscriptionModel.class);
        assertThat(service.getTranscriptionModel("pangu-stt")).isInstanceOf(PanguTranscriptionModel.class);
        assertThat(service.getTranscriptionModelNames()).containsExactly(
                "hunyuan-stt", "zhipu-stt", "doubao-stt", "pangu-stt");
    }

    @Test
    void refresh_unknownVendor_skipsThatEntryButKeepsOthers() {
        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        rerank.put("good", makeBailianCfg("good"));

        RerankModelConfig unknownCfg = new RerankModelConfig();
        unknownCfg.setProvider(null);
        unknownCfg.setApiKey("k");
        rerank.put("mystery", unknownCfg);

        properties.setRerank(rerank);

        service.refresh();

        assertThat(service.getRerankModel("good")).isNotNull().isInstanceOf(BailianRerankModel.class);
        assertThat(service.getRerankModel("mystery")).isNull();
        assertThat(service.getRerankModelNames()).containsExactly("good");
    }

    @Test
    void refresh_calledTwice_secondCallCoverReplacesExisting() {
        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        rerank.put("rerank-v1", makeBailianCfg("first"));
        properties.setRerank(rerank);

        service.refresh();
        RerankModel firstInstance = service.getRerankModel("rerank-v1");
        assertThat(firstInstance).isNotNull();

        Map<String, RerankModelConfig> rerank2 = new LinkedHashMap<>();
        rerank2.put("rerank-v1", makeBailianCfg("second"));
        properties.setRerank(rerank2);

        service.refresh();
        RerankModel secondInstance = service.getRerankModel("rerank-v1");
        assertThat(secondInstance).isNotNull().isNotSameAs(firstInstance);
        assertThat(service.getRerankModelNames()).containsExactly("rerank-v1");
    }

    @Test
    void refresh_withMissingHttpClient_skipsAllDimensionsGracefully() {
        @SuppressWarnings("unchecked")
        ObjectProvider<HttpClient> emptyProvider = mock(ObjectProvider.class);
        when(emptyProvider.getIfAvailable()).thenReturn(null);
        AiMultimodalServiceImpl noHttpService = new AiMultimodalServiceImpl(properties, emptyProvider);

        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        rerank.put("bailian", makeBailianCfg("bailian"));
        properties.setRerank(rerank);

        noHttpService.refresh();

        assertThat(noHttpService.getRerankModel("bailian")).isNull();
    }

    @Test
    void getRerankModel_unknownKey_returnsNull() {
        service.refresh();
        assertThat(service.getRerankModel("nonexistent")).isNull();
    }

    @Test
    void keySetGetters_returnUnmodifiableView() {
        Map<String, RerankModelConfig> rerank = new LinkedHashMap<>();
        rerank.put("a", makeBailianCfg("a"));
        rerank.put("b", makeBailianCfg("b"));
        properties.setRerank(rerank);
        service.refresh();

        assertThat(service.getRerankModelNames()).containsExactly("a", "b");
        assertThatThrownBy(() -> service.getRerankModelNames().add("c"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refresh_withBailianImageEmbedding_populatesImageEmbeddingsMap() {
        Map<String, ImageEmbeddingModelConfig> imageEmbedding = new LinkedHashMap<>();
        ImageEmbeddingModelConfig cfg = new ImageEmbeddingModelConfig();
        cfg.setProvider(ImageEmbeddingProvider.BAILIAN);
        cfg.setApiKey("sk-mm-test");
        cfg.setBaseUrl(null);
        cfg.setModel("multimodal-embedding-v1");
        imageEmbedding.put("default-mm", cfg);

        properties.setImageEmbedding(imageEmbedding);

        service.refresh();

        ImageEmbeddingModel model = service.getImageEmbeddingModel("default-mm");
        assertThat(model).isNotNull().isInstanceOf(BailianImageEmbeddingAdapter.class);
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(service.getImageEmbeddingModelNames()).containsExactly("default-mm");
    }

    @Test
    void refresh_imageEmbedding_unknownVendor_skipsButKeepsOthers() {
        Map<String, ImageEmbeddingModelConfig> imageEmbedding = new LinkedHashMap<>();

        ImageEmbeddingModelConfig good = new ImageEmbeddingModelConfig();
        good.setProvider(ImageEmbeddingProvider.BAILIAN);
        good.setApiKey("sk-good");
        good.setBaseUrl(null);
        good.setModel("multimodal-embedding-v1");
        imageEmbedding.put("good", good);

        ImageEmbeddingModelConfig bad = new ImageEmbeddingModelConfig();
        bad.setProvider(null);
        bad.setApiKey("sk-bad");
        imageEmbedding.put("mystery", bad);

        properties.setImageEmbedding(imageEmbedding);

        service.refresh();

        assertThat(service.getImageEmbeddingModel("good")).isNotNull().isInstanceOf(BailianImageEmbeddingAdapter.class);
        assertThat(service.getImageEmbeddingModel("mystery")).isNull();
        assertThat(service.getImageEmbeddingModelNames()).containsExactly("good");
    }

    @Test
    void getImageEmbeddingModel_unknownKey_returnsNull() {
        service.refresh();
        assertThat(service.getImageEmbeddingModel("nonexistent")).isNull();
    }

    // -------- helpers --------

    private static RerankModelConfig makeBailianCfg(String tag) {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.BAILIAN);
        cfg.setApiKey("sk-" + tag);
        cfg.setBaseUrl(null);
        cfg.setModel("gte-rerank");
        return cfg;
    }

    private static RerankModelConfig makeZhipuCfg(String tag) {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.ZHIPU);
        cfg.setApiKey("sk-" + tag);
        cfg.setBaseUrl(null);
        cfg.setModel("rerank");
        return cfg;
    }

    private static RerankModelConfig makePanguCfg(String tag) {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.PANGU);
        cfg.setAppCode("appcode-" + tag);
        cfg.setBaseUrl(null);
        cfg.setModel("pangu-rerank");
        return cfg;
    }

    private static TtsModelConfig makeTtsCfg(String vendor, boolean bearerOnly) {
        TtsModelConfig cfg = new TtsModelConfig();
        cfg.setProvider(TtsProvider.valueOf(vendor.toUpperCase()));
        fillAudioAuth(cfg, vendor, bearerOnly);
        cfg.setModel("model-" + vendor);
        return cfg;
    }

    private static SttModelConfig makeSttCfg(String vendor, boolean bearerOnly) {
        SttModelConfig cfg = new SttModelConfig();
        cfg.setProvider(SttProvider.valueOf(vendor.toUpperCase()));
        fillAudioAuth(cfg, vendor, bearerOnly);
        cfg.setModel("model-" + vendor);
        return cfg;
    }

    private static void fillAudioAuth(AbstractAudioModelConfig cfg, String vendor, boolean bearerOnly) {
        if (bearerOnly) {
            cfg.setApiKey("key-" + vendor);
        } else if ("pangu".equals(vendor)) {
            cfg.setAppCode("appcode-" + vendor);
        } else if ("hunyuan".equals(vendor)) {
            cfg.setSecretId("AKID-" + vendor);
            cfg.setSecretKey("SECRET-" + vendor);
            cfg.setRegion("ap-guangzhou");
            cfg.setEndpoint("endpoint-" + vendor + ".tencentcloudapi.com");
        } else {
            cfg.setApiKey("key-" + vendor);
            cfg.setAppId("appid-" + vendor);
            cfg.setResourceId("resource-" + vendor);
        }
    }
}
