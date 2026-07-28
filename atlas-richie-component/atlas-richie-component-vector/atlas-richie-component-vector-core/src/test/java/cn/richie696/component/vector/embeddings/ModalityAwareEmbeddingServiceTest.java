package cn.richie696.component.vector.embeddings;

import cn.richie696.component.vector.exceptions.UnsupportedModalityException;
import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.model.VectorContent;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModalityAwareEmbeddingServiceTest {

    @Test
    void routesTextAndImageToTheirConfiguredModels() {
        EmbeddingModel textModel = mock(EmbeddingModel.class);
        EmbeddingModel imageModel = mock(EmbeddingModel.class);
        when(textModel.embed("text")).thenReturn(new float[]{0.1f});
        when(imageModel.embed(startsWith("data:image/png;base64,"))).thenReturn(new float[]{0.2f});
        ModalityAwareEmbeddingService service = new ModalityAwareEmbeddingService(textModel, imageModel);

        assertThat(service.embed(Modality.TEXT, new VectorContent.TextContent("text", "text/plain")))
                .containsExactly(0.1f);
        assertThat(service.embed(Modality.IMAGE, new VectorContent.ImageContent(new byte[]{1}, "image/png")))
                .containsExactly(0.2f);
        verify(imageModel).embed(startsWith("data:image/png;base64,"));
    }

    @Test
    void rejectsImageWhenImageModelIsNotConfigured() {
        ModalityAwareEmbeddingService service = new ModalityAwareEmbeddingService(mock(EmbeddingModel.class), null);

        assertThatThrownBy(() -> service.embed(Modality.IMAGE,
                new VectorContent.ImageContent(new byte[]{1}, "image/png")))
                .isInstanceOf(UnsupportedModalityException.class);
    }
}
