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
package com.richie.component.vector.service.impl;

import com.richie.component.vector.embeddings.ModalityAwareEmbeddingService;
import com.richie.component.vector.exceptions.UnsupportedModalityException;
import com.richie.component.vector.model.Modality;
import com.richie.component.vector.model.VectorContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * {@link ModalityAwareEmbeddingService} 模态路由单元测试。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>TEXT/IMAGE 内容 → 对应 {@link EmbeddingModel} 分发</li>
 *   <li>content 类型错配 → IllegalArgumentException</li>
 *   <li>IMAGE 无模型 → UnsupportedModalityException</li>
 *   <li>{@link ModalityAwareEmbeddingService#supportsModality} 维度查询语义</li>
 *   <li>{@link ModalityAwareEmbeddingService#dimensionFor} 维度查询语义</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ModalityAwareEmbeddingServiceTest {

    @Mock
    private EmbeddingModel textModel;

    @Mock
    private EmbeddingModel imageModel;

    private ModalityAwareEmbeddingService serviceWithImage;
    private ModalityAwareEmbeddingService serviceWithoutImage;

    @BeforeEach
    void setUp() {
        serviceWithImage = new ModalityAwareEmbeddingService(textModel, imageModel);
        serviceWithoutImage = new ModalityAwareEmbeddingService(textModel, null);
    }

    // ==================== embed() — TEXT 路径 ====================

    @Test
    void embed_textRoutesThroughTextModel() {
        when(textModel.embed("hello world")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        VectorContent.TextContent content = new VectorContent.TextContent("hello world", "text/plain");

        float[] result = serviceWithImage.embed(Modality.TEXT, content);

        assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
        verify(textModel).embed("hello world");
        verify(imageModel, never()).embed(anyString());
    }

    @Test
    void embed_textContentWrongType_throwsIllegalArgumentException() {
        VectorContent.ImageContent wrongType = new VectorContent.ImageContent(new byte[]{1, 2, 3}, "image/png");

        assertThatThrownBy(() -> serviceWithImage.embed(Modality.TEXT, wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEXT 模态要求 TextContent")
                .hasMessageContaining("ImageContent");

        verify(textModel, never()).embed(anyString());
    }

    // ==================== embed() — IMAGE 路径 ====================

    @Test
    void embed_imageWithoutModel_throwsUnsupportedModalityException() {
        VectorContent.ImageContent image = new VectorContent.ImageContent(new byte[]{1, 2, 3}, "image/png");

        assertThatThrownBy(() -> serviceWithoutImage.embed(Modality.IMAGE, image))
                .isInstanceOf(UnsupportedModalityException.class)
                .hasMessageContaining("IMAGE 模态未配置");
    }

    @Test
    void embed_imageWithModelRoutesThroughImageModel() {
        byte[] rawBytes = new byte[]{0x10, 0x20, 0x30, (byte) 0xFF};
        String expectedDataUrl = "data:image/png;base64,"
                + Base64.getEncoder().encodeToString(rawBytes);
        float[] expectedVec = new float[]{0.5f, 0.6f, 0.7f};

        when(imageModel.embed(expectedDataUrl)).thenReturn(expectedVec);

        VectorContent.ImageContent image = new VectorContent.ImageContent(rawBytes, "image/png");
        float[] result = serviceWithImage.embed(Modality.IMAGE, image);

        assertThat(result).isSameAs(expectedVec);
        verify(imageModel).embed(argThat((String s) -> s.equals(expectedDataUrl)));
        verify(textModel, never()).embed(anyString());
    }

    @Test
    void embed_imageContentWrongType_throwsIllegalArgumentException() {
        VectorContent.TextContent wrongType = new VectorContent.TextContent("not an image", "text/plain");

        assertThatThrownBy(() -> serviceWithImage.embed(Modality.IMAGE, wrongType))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IMAGE 模态要求 ImageContent")
                .hasMessageContaining("TextContent");

        verify(imageModel, never()).embed(anyString());
    }

    @Test
    void embed_dataUrlFormatIsStable_acrossMimeTypes() {
        // 验证 base64 编码 + mimeType 前缀的稳定性 — 防止有人误改成 raw 字节
        byte[] payload = "中文-测试".getBytes(StandardCharsets.UTF_8);
        VectorContent.ImageContent jpeg = new VectorContent.ImageContent(payload, "image/jpeg");
        VectorContent.ImageContent webp = new VectorContent.ImageContent(payload, "image/webp");

        when(imageModel.embed(anyString())).thenReturn(new float[]{0.0f});

        serviceWithImage.embed(Modality.IMAGE, jpeg);
        serviceWithImage.embed(Modality.IMAGE, webp);

        verify(imageModel).embed(argThat((String s) -> s.startsWith("data:image/jpeg;base64,")));
        verify(imageModel).embed(argThat((String s) -> s.startsWith("data:image/webp;base64,")));
    }

    // ==================== supportsModality() ====================

    @Test
    void supportsModality_textAlwaysSupported() {
        assertThat(serviceWithImage.supportsModality(Modality.TEXT)).isTrue();
        assertThat(serviceWithoutImage.supportsModality(Modality.TEXT)).isTrue();
    }

    @Test
    void supportsModality_imageRequiresModel() {
        assertThat(serviceWithImage.supportsModality(Modality.IMAGE)).isTrue();
        assertThat(serviceWithoutImage.supportsModality(Modality.IMAGE)).isFalse();
    }

    @Test
    void supportsModality_nullReturnsFalse() {
        assertThat(serviceWithImage.supportsModality(null)).isFalse();
    }

    // ==================== dimensionFor() ====================

    @Test
    void dimensionFor_textReturnsTextModelDimension() {
        when(textModel.dimensions()).thenReturn(1536);

        assertThat(serviceWithImage.dimensionFor(Modality.TEXT)).isEqualTo(1536);
        assertThat(serviceWithoutImage.dimensionFor(Modality.TEXT)).isEqualTo(1536);
    }

    @Test
    void dimensionFor_imageReturnsZeroWhenNoModel() {
        assertThat(serviceWithoutImage.dimensionFor(Modality.IMAGE)).isZero();
    }

    @Test
    void dimensionFor_imageReturnsImageModelDimension() {
        when(imageModel.dimensions()).thenReturn(1024);

        assertThat(serviceWithImage.dimensionFor(Modality.IMAGE)).isEqualTo(1024);
    }

    @Test
    void dimensionFor_nullReturnsZero() {
        assertThat(serviceWithImage.dimensionFor(null)).isZero();
    }

    // ==================== 构造器契约 ====================

    @Test
    void constructor_nullTextModel_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> new ModalityAwareEmbeddingService(null, imageModel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("textModel");
    }
}