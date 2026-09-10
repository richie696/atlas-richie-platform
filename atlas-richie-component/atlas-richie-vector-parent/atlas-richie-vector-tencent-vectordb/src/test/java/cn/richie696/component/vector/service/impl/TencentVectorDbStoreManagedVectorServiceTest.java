package cn.richie696.component.vector.service.impl;

import cn.richie696.component.vector.model.VectorContent;
import cn.richie696.component.vector.model.VectorRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TencentVectorDbStoreManagedVectorServiceTest {

    @Test
    void removesTheReservedContentMetadataBeforeCallingTheTencentStore() {
        CapturingVectorStore store = new CapturingVectorStore();
        TencentVectorDbStoreManagedVectorService service = new TencentVectorDbStoreManagedVectorService(
                null, store, null, Map.of("documents", "documents"));
        VectorRecord record = new VectorRecord();
        record.setId("doc-1");
        record.setIndexName("documents");
        record.setContent(new VectorContent.TextContent("body", "text/plain"));
        record.setMetadata(Map.of("tenantId", "tenant-a"));

        service.upsert(record);

        Document document = store.documents.getFirst();
        assertThat(document.getText()).isEqualTo("body");
        assertThat(document.getMetadata())
                .containsEntry("tenantId", "tenant-a")
                .doesNotContainKey("content");
    }

    private static final class CapturingVectorStore implements VectorStore {
        private List<Document> documents = List.of();

        @Override
        public void add(List<Document> documents) {
            this.documents = List.copyOf(documents);
        }

        @Override
        public void delete(List<String> ids) {
        }

        @Override
        public void delete(Filter.Expression expression) {
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }
    }
}
