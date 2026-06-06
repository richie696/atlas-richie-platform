package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.config.support.VectorIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@VectorIntegrationTest
@TestPropertySource(properties = {
        "platform.component.vector.default-index=it-documents",
        "platform.component.vector.indexes.it-documents.name=it-documents",
        "platform.component.vector.indexes.it-documents.dimension=3"
})
class VectorPropertiesLoadIT {

    @Autowired
    private VectorProperties vectorProperties;

    @Test
    void vectorProperties_shouldBindIndexesFromConfig() {
        assertThat(vectorProperties.getDefaultIndex()).isEqualTo("it-documents");
        assertThat(vectorProperties.getIndexes()).containsKey("it-documents");
        assertThat(vectorProperties.getIndexes().get("it-documents").getDimension()).isEqualTo(3);
    }
}
