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
package cn.richie696.component.mcp.schema;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpSchemaValidationResult} 不可变 record 的语义：
 * 紧凑构造器对入参做防御性拷贝；{@link McpSchemaValidationResult#valid()} 是可身份比较的单例；
 * {@link McpSchemaValidationResult#isValid()} 与违规列表保持一致。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSchemaValidationResult 不可变载体")
class McpSchemaValidationResultTest {

    @Test
    @DisplayName("valid() 返回的违规列表为空且可身份复用")
    void validReturnsSharedSingleton() {
        McpSchemaValidationResult first = McpSchemaValidationResult.valid();
        McpSchemaValidationResult second = McpSchemaValidationResult.valid();

        assertThat(first).isSameAs(second);
        assertThat(first.isValid()).isTrue();
        assertThat(first.violations()).isEmpty();
    }

    @Test
    @DisplayName("构造器拷贝入参列表：外部突变不影响结果")
    void constructorCopiesViolationsList() {
        List<McpSchemaViolation> source = new ArrayList<>();
        source.add(new McpSchemaViolation("/a", "/b", "c", "d"));

        McpSchemaValidationResult result = new McpSchemaValidationResult(source);
        source.clear();

        assertThat(result.violations()).hasSize(1);
        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("返回的 violations 列表是不可变视图")
    void violationsListIsUnmodifiable() {
        McpSchemaValidationResult result = new McpSchemaValidationResult(List.of(
                new McpSchemaViolation("/a", "/b", "c", "d")));

        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> result.violations().clear());
    }

    @Test
    @DisplayName("isValid() 与 violations() 大小一致")
    void isValidMatchesViolationsSize() {
        McpSchemaValidationResult valid = new McpSchemaValidationResult(List.of());
        McpSchemaValidationResult invalid = new McpSchemaValidationResult(List.of(
                new McpSchemaViolation("/x", "/y", "z", "m")));

        assertThat(valid.isValid()).isTrue();
        assertThat(invalid.isValid()).isFalse();
    }
}
