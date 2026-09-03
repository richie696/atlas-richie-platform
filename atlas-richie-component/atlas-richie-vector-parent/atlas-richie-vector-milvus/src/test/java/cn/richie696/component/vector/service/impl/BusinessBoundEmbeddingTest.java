package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.config.MilvusConfig;
import cn.richie696.component.vector.model.SearchOptions;
import cn.richie696.component.vector.model.VectorContent;
import cn.richie696.component.vector.model.VectorRecord;
import io.milvus.client.MilvusServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessBoundEmbeddingTest {
    static class RecordingService extends MilvusVectorServiceImpl {
        final List<String> writes = new ArrayList<>();
        final List<Float> queries = new ArrayList<>();
        RecordingService(EmbeddingModel unscoped) {
            super(null, mock(VectorStore.class), unscoped, new MilvusConfig(), mock(MilvusServiceClient.class));
        }
        @Override protected void addEmbeddings(String index, List<Document> docs) { writes.add(index); }
        @Override protected List<Document> similaritySearchByVector(String index, float[] vector, int k, double min, String filter) {
            queries.add(vector[0]); return List.of();
        }
    }
    @Test void writesAndQueriesUseTheSameBusinessBindingNotGlobalModel() {
        EmbeddingModel unscoped = mock(EmbeddingModel.class), a = mock(EmbeddingModel.class), b = mock(EmbeddingModel.class);
        when(a.embed("question")).thenReturn(new float[]{1f,0f});
        when(b.embed("question")).thenReturn(new float[]{2f,0f});
        RecordingService service = new RecordingService(unscoped);
        service.setIndexEmbeddingModelResolver(index -> switch(index) { case "a" -> a; case "b" -> b; default -> throw new IllegalStateException("unbound"); });
        for (String index : List.of("a", "b")) {
            service.upsert(new VectorRecord().setId(index).setIndexName(index).setContent(new VectorContent.TextContent("question", "text/plain")));
            service.searchByText(index, "question", 1, SearchOptions.builder().build());
        }
        assertEquals(List.of("a", "b"), service.writes);
        assertEquals(List.of(1f, 2f), service.queries);
        verify(a, times(2)).embed("question"); verify(b, times(2)).embed("question");
        verifyNoInteractions(unscoped);
    }
    @Test void missingBindingFailsClosedForWriteAndSearch() {
        EmbeddingModel unscoped = mock(EmbeddingModel.class);
        RecordingService service = new RecordingService(unscoped);
        service.setIndexEmbeddingModelResolver(index -> { throw new IllegalStateException("unbound"); });
        assertThrows(IllegalStateException.class, () -> service.searchByText("legacy", "q", 1, SearchOptions.builder().build()));
        assertThrows(IllegalStateException.class, () -> service.upsert(new VectorRecord().setIndexName("legacy")
                .setContent(new VectorContent.TextContent("q", "text/plain"))));
        assertTrue(service.writes.isEmpty()); assertTrue(service.queries.isEmpty()); verifyNoInteractions(unscoped);
    }
}
