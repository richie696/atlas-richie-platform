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
package com.richie.component.mongodb.builder;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void getTotalPages_withExactDivision_shouldReturnCorrectPages() {
        PageResult<String> page = new PageResult<>(List.of("a", "b"), 20, 1, 10);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    void getTotalPages_withRemainder_shouldRoundUp() {
        PageResult<String> page = new PageResult<>(List.of("a"), 105, 1, 10);
        assertThat(page.getTotalPages()).isEqualTo(11);
    }

    @Test
    void getTotalPages_withZeroTotal_shouldReturnZero() {
        PageResult<String> page = new PageResult<>(List.of(), 0, 1, 10);
        assertThat(page.getTotalPages()).isEqualTo(0);
    }

    @Test
    void getTotalPages_withTotalLessThanPageSize_shouldReturnOne() {
        PageResult<String> page = new PageResult<>(List.of("a", "b"), 5, 1, 10);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void getTotalPages_withOneItem_shouldReturnOne() {
        PageResult<String> page = new PageResult<>(List.of("a"), 1, 1, 10);
        assertThat(page.getTotalPages()).isEqualTo(1);
    }

    @Test
    void constructor_shouldInitializeAllFields() {
        List<String> content = List.of("a", "b", "c");
        PageResult<String> page = new PageResult<>(content, 100, 5, 20);

        assertThat(page.getContent()).isEqualTo(content);
        assertThat(page.getTotal()).isEqualTo(100L);
        assertThat(page.getPageNum()).isEqualTo(5);
        assertThat(page.getPageSize()).isEqualTo(20);
    }
}
