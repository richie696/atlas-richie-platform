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

class SearchOptionsTest {

    @Test
    void defaults_rerankTrueOtherFieldsNull() {
        SearchOptions opts = SearchOptions.builder().build();

        // 默认开 rerank（仅文本搜索路径生效；图片搜索被忽略）
        assertThat(opts.getRerank()).isTrue();
        assertThat(opts.getMinScore()).isNull();
        assertThat(opts.getFilterExpression()).isNull();
        assertThat(opts.getNamespace()).isNull();
        assertThat(opts.getLimit()).isNull();
        assertThat(opts.getType()).isNull();
    }

    @Test
    void builder_setsAllFieldsIndependently() {
        SearchOptions opts = SearchOptions.builder()
                .rerank(false)
                .minScore(0.5)
                .filterExpression("status = 'active'")
                .namespace("tenant-1")
                .limit(20)
                .type("document")
                .build();

        assertThat(opts.getRerank()).isFalse();
        assertThat(opts.getMinScore()).isEqualTo(0.5);
        assertThat(opts.getFilterExpression()).isEqualTo("status = 'active'");
        assertThat(opts.getNamespace()).isEqualTo("tenant-1");
        assertThat(opts.getLimit()).isEqualTo(20);
        assertThat(opts.getType()).isEqualTo("document");
    }

    @Test
    void rerankFalse_isRespectedAfterBuilder() {
        SearchOptions opts = SearchOptions.builder().rerank(false).build();

        assertThat(opts.getRerank()).isFalse();
    }
}
