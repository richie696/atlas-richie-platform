/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.vector.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ElasticsearchConfig}.
 * Verifies that @ConfigurationProperties binding works correctly for all fields.
 */
class ElasticsearchConfigTest {

    @Test
    void defaults_shouldHaveSensibleValues() {
        ElasticsearchConfig config = new ElasticsearchConfig();

        assertThat(config.getClusterUrl()).isEqualTo("http://localhost:9200");
        assertThat(config.getConnectTimeout()).isEqualTo(5000);
        assertThat(config.getSocketTimeout()).isEqualTo(30000);
        assertThat(config.isContentCompressionEnabled()).isTrue();
    }

    @Test
    void setters_shouldUpdateValues() {
        ElasticsearchConfig config = new ElasticsearchConfig();

        config.setClusterUrl("http://es:9200");
        config.setConnectTimeout(10000);
        config.setSocketTimeout(60000);
        config.setContentCompressionEnabled(false);

        assertThat(config.getClusterUrl()).isEqualTo("http://es:9200");
        assertThat(config.getConnectTimeout()).isEqualTo(10000);
        assertThat(config.getSocketTimeout()).isEqualTo(60000);
        assertThat(config.isContentCompressionEnabled()).isFalse();
    }

    @Test
    void toString_shouldContainAllFields() {
        ElasticsearchConfig config = new ElasticsearchConfig();
        config.setClusterUrl("http://es:9200");
        config.setConnectTimeout(10000);
        config.setSocketTimeout(60000);
        config.setContentCompressionEnabled(false);

        String str = config.toString();

        assertThat(str).contains("clusterUrl");
        assertThat(str).contains("connectTimeout");
        assertThat(str).contains("socketTimeout");
        assertThat(str).contains("contentCompressionEnabled");
    }
}
