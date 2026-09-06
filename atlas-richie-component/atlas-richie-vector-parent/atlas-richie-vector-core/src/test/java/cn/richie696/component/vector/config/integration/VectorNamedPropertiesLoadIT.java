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
package cn.richie696.component.vector.config.integration;

import cn.richie696.component.vector.config.VectorProperties;
import cn.richie696.component.vector.config.support.VectorIntegrationTest;
import cn.richie696.component.vector.model.Modality;
import cn.richie696.component.vector.topology.EmbeddingNormalization;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@VectorIntegrationTest
@TestPropertySource(properties = {
        "platform.component.vector.connections.milvus-main.provider=milvus",
        "platform.component.vector.connections.milvus-main.settings.host=milvus.invalid",
        "platform.component.vector.connections.pg-shared.provider=postgresql",
        "platform.component.vector.connections.pg-shared.settings.jdbc-url=jdbc:postgresql://invalid/vector",
        "platform.component.vector.stores.primary-search.connection-ref=milvus-main",
        "platform.component.vector.stores.primary-search.embedding-model-ref=primaryEmbeddingModel",
        "platform.component.vector.stores.primary-search.embedding-normalization=unit-l2",
        "platform.component.vector.stores.primary-search.embedding-modalities[0]=text",
        "platform.component.vector.stores.primary-search.embedding-modalities[1]=image",
        "platform.component.vector.stores.primary-search.default-index=primary-documents",
        "platform.component.vector.stores.primary-search.required-capabilities[0]=ACL_FILTER",
        "platform.component.vector.stores.primary-search.query-defaults.top-k=25",
        "platform.component.vector.stores.primary-search.query-defaults.min-score=0.35",
        "platform.component.vector.stores.primary-search.query-defaults.candidate-limit=80",
        "platform.component.vector.stores.primary-search.query-defaults.timeout=15s",
        "platform.component.vector.stores.primary-search.query-defaults.consistency=provider-default",
        "platform.component.vector.stores.primary-search.query-defaults.return-fields[0]=content",
        "platform.component.vector.stores.normalized-content.connection-ref=pg-shared"
})
class VectorNamedPropertiesLoadIT {

    @Autowired
    private VectorProperties vectorProperties;

    @Test
    void shouldBindNamedTopologyAndPreserveOptionalDefaults() {
        vectorProperties.validateNamedTopology();

        assertThat(vectorProperties.getConnections()).containsOnlyKeys("milvus-main", "pg-shared");
        assertThat(vectorProperties.getStores()).containsOnlyKeys("primary-search", "normalized-content");
        assertThat(vectorProperties.getStores().get("primary-search").getConnectionRef()).isEqualTo("milvus-main");
        assertThat(vectorProperties.getStores().get("primary-search").getRequiredCapabilities())
                .containsExactly("ACL_FILTER");
        assertThat(vectorProperties.getStores().get("primary-search").getEmbeddingNormalization())
                .isEqualTo(EmbeddingNormalization.UNIT_L2);
        assertThat(vectorProperties.getStores().get("primary-search").getEmbeddingModalities())
                .containsExactlyInAnyOrder(Modality.TEXT, Modality.IMAGE);
        VectorProperties.QueryDefaultsConfig queryDefaults =
                vectorProperties.getStores().get("primary-search").getQueryDefaults();
        assertThat(queryDefaults.getTopK()).isEqualTo(25);
        assertThat(queryDefaults.getMinScore()).isEqualTo(0.35D);
        assertThat(queryDefaults.getCandidateLimit()).isEqualTo(80);
        assertThat(queryDefaults.getTimeout()).isEqualTo(java.time.Duration.ofSeconds(15));
        assertThat(queryDefaults.getReturnFields()).containsExactly("content");

        VectorProperties.StoreConfig defaultedStore = vectorProperties.getStores().get("normalized-content");
        assertThat(defaultedStore.getEmbeddingModelRef()).isEqualTo("aiEmbeddingModel");
        assertThat(defaultedStore.getEmbeddingNormalization()).isEqualTo(EmbeddingNormalization.UNSPECIFIED);
        assertThat(defaultedStore.getEmbeddingModalities()).containsExactly(Modality.TEXT);
        assertThat(defaultedStore.getDefaultIndex()).isEqualTo("documents");
        assertThat(defaultedStore.isRequired()).isTrue();
        assertThat(defaultedStore.getRequiredCapabilities()).isEmpty();
        assertThat(defaultedStore.getQueryDefaults().getTopK()).isNull();
        assertThat(defaultedStore.getQueryDefaults().getReturnFields()).isEmpty();
        assertThat(defaultedStore.getIndexes()).isEmpty();
    }
}
