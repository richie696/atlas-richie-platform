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
import java.util.Map;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class BusinessBoundEmbeddingTest {
    static class RecordingService extends MilvusVectorServiceImpl {
        final List<String> writes = new ArrayList<>();
        final List<Float> queries = new ArrayList<>();
        RecordingService(EmbeddingModel unscoped) {
            super(null, null, unscoped, new MilvusConfig(), null);
        }
        @Override protected void addEmbeddings(String index, List<Document> docs) { writes.add(index); }
        @Override protected List<Document> similaritySearchByVector(String index, float[] vector, int k, double min, String filter) {
            queries.add(vector[0]); return List.of();
        }
        @Override protected List<Document> similaritySearchByVector(String index, float[] vector, int k, double min,
                                                                    String filter, Map<String, Integer> providerSearchParameters) {
            queries.add(vector[0]); return List.of();
        }
    }
    @Test void writesAndQueriesUseTheSameBusinessBindingNotGlobalModel() {
        EmbeddingStub unscoped = embedding(9f), a = embedding(1f), b = embedding(2f);
        RecordingService service = new RecordingService(unscoped.model());
        service.setIndexEmbeddingModelResolver(index -> switch(index) {
            case "a" -> a.model(); case "b" -> b.model(); default -> throw new IllegalStateException("unbound");
        });
        for (String index : List.of("a", "b")) {
            service.upsert(new VectorRecord().setId(index).setIndexName(index).setContent(new VectorContent.TextContent("question", "text/plain")));
            service.searchByText(index, "question", 1, SearchOptions.builder().build());
        }
        assertEquals(List.of("a", "b"), service.writes);
        assertEquals(List.of(1f, 2f), service.queries);
        assertEquals(2, a.calls()); assertEquals(2, b.calls());
        assertEquals(0, unscoped.calls());
    }
    @Test void missingBindingFailsClosedForWriteAndSearch() {
        EmbeddingStub unscoped = embedding(9f);
        RecordingService service = new RecordingService(unscoped.model());
        service.setIndexEmbeddingModelResolver(index -> { throw new IllegalStateException("unbound"); });
        assertThrows(IllegalStateException.class, () -> service.searchByText("legacy", "q", 1, SearchOptions.builder().build()));
        assertThrows(IllegalStateException.class, () -> service.upsert(new VectorRecord().setIndexName("legacy")
                .setContent(new VectorContent.TextContent("q", "text/plain"))));
        assertTrue(service.writes.isEmpty()); assertTrue(service.queries.isEmpty()); assertEquals(0, unscoped.calls());
    }

    private static EmbeddingStub embedding(float firstValue) {
        AtomicInteger calls = new AtomicInteger();
        EmbeddingModel model = (EmbeddingModel) Proxy.newProxyInstance(
                EmbeddingModel.class.getClassLoader(),
                new Class<?>[]{EmbeddingModel.class},
                (proxy, method, args) -> {
                    if ("embed".equals(method.getName()) && args != null && args.length == 1
                            && args[0] instanceof String) {
                        calls.incrementAndGet();
                        return new float[]{firstValue, 0f};
                    }
                    if ("toString".equals(method.getName())) return "StubEmbeddingModel";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) return proxy == args[0];
                    if (method.getReturnType() == boolean.class) return false;
                    if (method.getReturnType() == int.class) return 2;
                    return null;
                });
        return new EmbeddingStub(model, calls);
    }

    private record EmbeddingStub(EmbeddingModel model, AtomicInteger callCounter) {
        int calls() { return callCounter.get(); }
    }
}
