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
package com.richie.component.vector.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchOptionsTest {

    @Test
    void defaults_vectorWeight07_keywordWeight03() {
        HybridSearchOptions opts = HybridSearchOptions.builder().build();

        assertThat(opts.getVectorWeight()).isEqualTo(0.7);
        assertThat(opts.getKeywordWeight()).isEqualTo(0.3);
        assertThat(opts.getKeywordQuery()).isNull();
        assertThat(opts.getSearchOptions()).isNull();
    }

    @Test
    void builder_overridesAllFields() {
        SearchOptions inner = SearchOptions.builder().minScore(0.42).build();
        HybridSearchOptions opts = HybridSearchOptions.builder()
                .vectorWeight(0.5)
                .keywordWeight(0.5)
                .keywordQuery("alpha")
                .searchOptions(inner)
                .build();

        assertThat(opts.getVectorWeight()).isEqualTo(0.5);
        assertThat(opts.getKeywordWeight()).isEqualTo(0.5);
        assertThat(opts.getKeywordQuery()).isEqualTo("alpha");
        assertThat(opts.getSearchOptions()).isSameAs(inner);
    }

    @Test
    void weightsSumCanExceedOne_semanticIsProviderSpecific() {
        // vectorWeight + keywordWeight 强约束由 provider 自行校验（如 Elasticsearch 用
        // linear 组合时通常要求和=1；Weaviate 允许独立设置），组件层只透传。
        HybridSearchOptions opts = HybridSearchOptions.builder()
                .vectorWeight(0.9)
                .keywordWeight(0.9)
                .build();

        assertThat(opts.getVectorWeight() + opts.getKeywordWeight()).isEqualTo(1.8);
    }
}
