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

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@link McpSchemaDefinitionException} 的两种构造形态（仅消息 / 带违规列表 + 根因），
 * 以及 {@link IllegalArgumentException} 继承带来的可观测性：消息、根因、违规列表在
 * {@code toString()} 与 {@link Throwable#getMessage()} 中正确透传。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpSchemaDefinitionException 异常语义")
class McpSchemaDefinitionExceptionTest {

    @Test
    @DisplayName("单参构造器生成空违规列表与 null 根因")
    void singleArgConstructorProducesEmptyViolations() {
        McpSchemaDefinitionException exception = new McpSchemaDefinitionException("bad schema");

        assertThat(exception.getMessage()).isEqualTo("bad schema");
        assertThat(exception.violations()).isEmpty();
        assertThat(exception.getCause()).isNull();
        assertThat(exception).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("完整构造器保留违规列表与根因，并做防御性不可变拷贝")
    void fullConstructorPreservesViolationsAndCause() {
        IOException cause = new IOException("boom");
        McpSchemaViolation violation = new McpSchemaViolation(
                "/name", "/properties/name/type", "type", "must be string");
        McpSchemaDefinitionException exception = new McpSchemaDefinitionException(
                "schema invalid", List.of(violation), cause);

        assertThat(exception.getMessage()).isEqualTo("schema invalid");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThat(exception.violations()).hasSize(1).containsExactly(violation);
    }

    @Test
    @DisplayName("null violations 会被规范化为空列表而非 NPE")
    void nullViolationsAreNormalizedToEmptyList() {
        McpSchemaDefinitionException exception = new McpSchemaDefinitionException(
                "anything", null, null);

        assertThat(exception.violations()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("违规列表被拷贝为不可变视图，外部突变不应影响异常状态")
    void violationListIsImmutableSnapshot() {
        java.util.List<McpSchemaViolation> mutable = new java.util.ArrayList<>();
        mutable.add(new McpSchemaViolation("a", "b", "c", "d"));
        McpSchemaDefinitionException exception = new McpSchemaDefinitionException(
                "msg", mutable, null);

        // 之后外部修改源 List 不影响已捕获快照
        mutable.clear();
        assertThat(exception.violations()).hasSize(1);

        // 直接修改返回值会触发 UnsupportedOperationException
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class,
                () -> exception.violations().clear());
    }
}
