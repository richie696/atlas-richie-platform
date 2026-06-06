package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.support.AbstractVectorRedisIntegrationTest;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "platform.component.vector.provider=redis",
        "platform.component.vector.default-index=it-vectors",
        "spring.ai.vectorstore.redis.index-name=it-vectors",
        "spring.ai.vectorstore.redis.prefix=it-v:",
        "spring.ai.vectorstore.redis.initialize-schema=true"
})
class RedisVectorDocumentOpsIT extends AbstractVectorRedisIntegrationTest {

    @Autowired(required = false)
    private VectorService vectorService;

    @Test
    void addAndSearchDocument_whenVectorServicePresent() {
        if (vectorService == null) {
            return;
        }
        VectorDocument doc = new VectorDocument();
        doc.setContent("wave-b integration sample");
        String id = vectorService.addDocument(doc);
        assertThat(id).isNotBlank();
        assertThat(vectorService.getDocument(id)).isNotNull();
        assertThat(vectorService.searchByText("integration", 3)).isNotNull();
    }
}
