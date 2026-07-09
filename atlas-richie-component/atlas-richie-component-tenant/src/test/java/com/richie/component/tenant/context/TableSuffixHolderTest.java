/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.tenant.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TableSuffixHolder — Table 模式表名后缀持有器")
class TableSuffixHolderTest {

    @AfterEach
    void tearDown() {
        TableSuffixHolder.clear();
    }

    @Test
    @DisplayName("初始 get() 返回 null")
    void initialGetReturnsNull() {
        assertThat(TableSuffixHolder.get()).isNull();
    }

    @Test
    @DisplayName("set 后 get 返回对应后缀")
    void setAndGet() {
        TableSuffixHolder.set("_1001");
        assertThat(TableSuffixHolder.get()).isEqualTo("_1001");
    }

    @Test
    @DisplayName("clear 后 get 返回 null")
    void clearRemovesValue() {
        TableSuffixHolder.set("_1001");
        TableSuffixHolder.clear();
        assertThat(TableSuffixHolder.get()).isNull();
    }
}
