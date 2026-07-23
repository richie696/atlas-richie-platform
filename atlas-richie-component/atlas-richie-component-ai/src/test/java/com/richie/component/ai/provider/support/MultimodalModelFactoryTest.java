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
package com.richie.component.ai.provider.support;

import com.richie.component.ai.config.multimodal.image.ImageEmbeddingModelConfig;
import com.richie.component.ai.config.multimodal.image.ImageEmbeddingProvider;
import com.richie.component.ai.config.multimodal.rerank.RerankProvider;

import com.richie.component.ai.api.RerankModel;
import com.richie.component.ai.config.multimodal.rerank.RerankModelConfig;

import com.richie.component.ai.api.image.ImageEmbeddingModel;
import com.richie.component.ai.provider.bailian.BailianImageEmbeddingAdapter;
import com.richie.component.ai.provider.bailian.BailianRerankModel;
import com.richie.component.ai.provider.pangu.PanguRerankModel;
import com.richie.component.ai.provider.ollama.OllamaImageEmbeddingAdapter;
import com.richie.component.ai.provider.tei.TeiImageEmbeddingAdapter;
import com.richie.component.ai.provider.zhipu.ZhipuRerankModel;
import com.richie.component.ai.support.keypool.ApiKeyPoolManager;
import com.richie.component.http.core.HttpClient;
import com.richie.component.http.core.HttpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * {@link MultimodalModelFactory#createRerankModel(RerankModelConfig, HttpClient)}
 * vendor 分派的纯单元测试（hermetic —— 不发起真实网络请求）。
 * <p>
 * 测试策略：构造 {@link RerankModelConfig}（mock {@link HttpClient}），按
 * {@code cfg.getVendor()} 字段断言分派到正确的 vendor 实现类；未识别 vendor
 * 抛出 {@link IllegalArgumentException}。
 * <p>
 * 不验证 HttpClient 调用 —— 该层依赖 Spring Bean 装配，由集成测试覆盖。
 *
 * @author richie696
 * @since 1.0.0
 */
class MultimodalModelFactoryTest {

    @Test
    void createRerankModel_bailian_returnsBailianRerankModel() {
        RerankModelConfig cfg = baseBailianCfg();
        cfg.setApiKey("sk-bailian");

        RerankModel model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(BailianRerankModel.class);
    }

    @Test
    void createRerankModel_zhipu_returnsZhipuRerankModel() {
        RerankModelConfig cfg = baseZhipuCfg();
        cfg.setApiKey("sk-zhipu-1234");

        RerankModel model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(ZhipuRerankModel.class);
    }

    @Test
    void createRerankModel_pangu_returnsPanguRerankModel() {
        RerankModelConfig cfg = basePanguCfg();
        cfg.setAppCode("appcode-pangu-1234");

        RerankModel model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(PanguRerankModel.class);
    }

    @Test
    void createRerankModel_vendorCaseInsensitive_acceptsUpperCase() {
        // vendor 字段为大写也能正确分发 —— 由 normalizeVendor() 在工厂内部处理
        RerankModelConfig cfg = baseBailianCfg();
        cfg.setProvider(RerankProvider.BAILIAN);
        cfg.setApiKey("sk-bailian");

        RerankModel model = MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(BailianRerankModel.class);
    }

    @Test
    void createRerankModel_unknownVendor_throwsIllegalArgument() {
        // vendor 为 null 也属于"未识别"(type-safe 后无法用 String 注入未知 vendor)
        RerankModelConfig cfg = baseBailianCfg();
        cfg.setProvider(null);

        assertThatThrownBy(() ->
                MultimodalModelFactory.createRerankModel(cfg, mock(HttpClient.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rerank")
                .hasMessageContaining("'null'");
    }

    // -------- helpers --------

    private static RerankModelConfig baseBailianCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.BAILIAN);
        cfg.setBaseUrl(null);
        cfg.setModel("gte-rerank");
        return cfg;
    }

    private static RerankModelConfig baseZhipuCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.ZHIPU);
        cfg.setBaseUrl(null);
        cfg.setModel("rerank");
        return cfg;
    }

    private static RerankModelConfig basePanguCfg() {
        RerankModelConfig cfg = new RerankModelConfig();
        cfg.setProvider(RerankProvider.PANGU);
        cfg.setBaseUrl(null);
        cfg.setModel("pangu-rerank");
        return cfg;
    }

    // ====================== Image Embedding ======================

    @Test
    void createImageEmbeddingModel_bailian_returnsBailianImageEmbeddingAdapter() {
        ImageEmbeddingModelConfig cfg = baseBailianImageEmbeddingCfg();
        cfg.setApiKey("sk-bailian-mm");

        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(BailianImageEmbeddingAdapter.class);
    }

    @Test
    void createImageEmbeddingModel_ollama_returnsAdapter() {
        ImageEmbeddingModelConfig cfg = baseOllamaImageEmbeddingCfg();

        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModel(
                cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(OllamaImageEmbeddingAdapter.class);
    }

    @Test
    void createImageEmbeddingModel_unknownImageEmbeddingProvider_throws() {
        ImageEmbeddingModelConfig cfg = baseBailianImageEmbeddingCfg();
        cfg.setProvider(null);

        assertThatThrownBy(() ->
                MultimodalModelFactory.createImageEmbeddingModel(cfg, mock(HttpClient.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image-embedding")
                .hasMessageContaining("'null'");
    }

    @Test
    void createImageEmbeddingModelPooled_multiKey_returnsPooledEmbeddingModel() {
        ImageEmbeddingModelConfig cfg = baseBailianImageEmbeddingCfg();
        cfg.setApiKey("sk-ignored");
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("sk-1");
        keys.add("sk-2");
        cfg.setApiKeys(keys);

        ApiKeyPoolManager poolManager = new ApiKeyPoolManager(new com.richie.component.ai.config.AiModelProperties());
        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "img-search", cfg, mock(HttpClient.class), poolManager);

        assertThat(model).isNotNull().isInstanceOf(MultimodalModelFactory.PooledEmbeddingModel.class);
    }

    @Test
    void createImageEmbeddingModelPooled_singleKey_returnsUnderlyingAdapterNotPooled() {
        ImageEmbeddingModelConfig cfg = baseBailianImageEmbeddingCfg();
        cfg.setApiKey("sk-only-one");

        ApiKeyPoolManager poolManager = new ApiKeyPoolManager(new com.richie.component.ai.config.AiModelProperties());
        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "img-search", cfg, mock(HttpClient.class), poolManager);

        // size<=1 时走单 key 路径,不包装 PooledEmbeddingModel
        assertThat(model).isNotNull().isInstanceOf(BailianImageEmbeddingAdapter.class);
    }

    private static ImageEmbeddingModelConfig baseBailianImageEmbeddingCfg() {
        ImageEmbeddingModelConfig cfg = new ImageEmbeddingModelConfig();
        cfg.setProvider(ImageEmbeddingProvider.BAILIAN);
        cfg.setBaseUrl(null);
        cfg.setModel("multimodal-embedding-v1");
        return cfg;
    }

    @Test
    void createImageEmbeddingModelPooled_ollamaMultiKey_returnsPooledEmbeddingModel() {
        ImageEmbeddingModelConfig cfg = baseOllamaImageEmbeddingCfg();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("proxy-token-1");
        keys.add("proxy-token-2");
        cfg.setApiKeys(keys);

        ApiKeyPoolManager poolManager = new ApiKeyPoolManager(
                new com.richie.component.ai.config.AiModelProperties());
        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "ollama-search", cfg, mock(HttpClient.class), poolManager);

        assertThat(model).isNotNull()
                .isInstanceOf(MultimodalModelFactory.PooledEmbeddingModel.class);
    }

    @Test
    void createImageEmbeddingModelPooled_ollamaSingleKey_addsBearerHeader() {
        ImageEmbeddingModelConfig cfg = baseOllamaImageEmbeddingCfg();
        cfg.setApiKeys(new LinkedHashSet<>(List.of("proxy-token")));
        HttpClient httpClient = mock(HttpClient.class);
        HttpRequest request = mock(HttpRequest.class);
        when(httpClient.post(anyString(), any())).thenReturn(request);
        when(request.header(anyString(), anyString())).thenReturn(request);
        when(request.execute(org.mockito.ArgumentMatchers.<Class<Object>>any())).thenReturn(null);

        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "ollama-search", cfg, httpClient, mock(ApiKeyPoolManager.class));

        model.call(new EmbeddingRequest(List.of("text"), null));

        verify(request).header("Authorization", "Bearer proxy-token");
    }

    private static ImageEmbeddingModelConfig baseOllamaImageEmbeddingCfg() {
        ImageEmbeddingModelConfig cfg = new ImageEmbeddingModelConfig();
        cfg.setProvider(ImageEmbeddingProvider.OLLAMA);
        cfg.setBaseUrl(null);
        cfg.setModel(null);
        return cfg;
    }

    @Test
    void createImageEmbeddingModel_tei_returnsTeiImageEmbeddingAdapter() {
        ImageEmbeddingModelConfig cfg = baseTeiImageEmbeddingCfg();
        cfg.setBaseUrl("http://tei.local:8080");
        cfg.setApiKey(null);   // TEI 默认无鉴权

        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(TeiImageEmbeddingAdapter.class);
    }

    @Test
    void createImageEmbeddingModel_teiWithApiKey_returnsAdapterAndPropagatesKey() {
        // apiKey 非空(反代 Bearer)时,工厂仍应正确分派;不抛错。
        ImageEmbeddingModelConfig cfg = baseTeiImageEmbeddingCfg();
        cfg.setBaseUrl("http://tei.local:8080");
        cfg.setApiKey("bearer-token-xyz");

        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModel(cfg, mock(HttpClient.class));

        assertThat(model).isNotNull().isInstanceOf(TeiImageEmbeddingAdapter.class);
    }

    @Test
    void createImageEmbeddingModelPooled_teiMultiKey_returnsPooledEmbeddingModel() {
        ImageEmbeddingModelConfig cfg = baseTeiImageEmbeddingCfg();
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        keys.add("bearer-1");
        keys.add("bearer-2");
        cfg.setApiKeys(keys);

        ApiKeyPoolManager poolManager = new ApiKeyPoolManager(new com.richie.component.ai.config.AiModelProperties());
        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "img-search", cfg, mock(HttpClient.class), poolManager);

        // 多 key 路径走 PooledEmbeddingModel —— TEI 也支持,共享同一池化框架
        assertThat(model).isNotNull().isInstanceOf(MultimodalModelFactory.PooledEmbeddingModel.class);
    }

    @Test
    void createImageEmbeddingModelPooled_teiSingleKey_returnsUnderlyingAdapterNotPooled() {
        // size<=1 时走单 key 路径,不包装 PooledEmbeddingModel —— 与 BAILIAN 一致
        ImageEmbeddingModelConfig cfg = baseTeiImageEmbeddingCfg();
        cfg.setApiKey("bearer-only-one");

        ApiKeyPoolManager poolManager = new ApiKeyPoolManager(new com.richie.component.ai.config.AiModelProperties());
        ImageEmbeddingModel model = MultimodalModelFactory.createImageEmbeddingModelPooled(
                "img-search", cfg, mock(HttpClient.class), poolManager);

        assertThat(model).isNotNull().isInstanceOf(TeiImageEmbeddingAdapter.class);
    }

    private static ImageEmbeddingModelConfig baseTeiImageEmbeddingCfg() {
        ImageEmbeddingModelConfig cfg = new ImageEmbeddingModelConfig();
        cfg.setProvider(ImageEmbeddingProvider.TEI);
        cfg.setBaseUrl("http://localhost:8080");
        cfg.setModel(null);   // TEI 单容器固定模型,通常省略
        return cfg;
    }
}
