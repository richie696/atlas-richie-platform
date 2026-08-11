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
package cn.richie696.component.mcp.server.spring.boot;

import cn.richie696.component.mcp.api.annotation.McpArgument;
import cn.richie696.component.mcp.api.server.McpArgumentBindingException;
import cn.richie696.component.mcp.api.server.McpArgumentMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 验证 {@link JacksonMcpArgumentBinder} 的绑定语义：null 必填、默认回填、Optional 适配、
 * Jackson 类型转换、IllegalArgumentException → McpArgumentBindingException 翻译。
 *
 * @author richie696
 * @since 2026-08-11
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JacksonMcpArgumentBinder 参数绑定")
class JacksonMcpArgumentBinderTest {

    @Mock
    private ObjectMapper mockObjectMapper;

    @Nested
    @DisplayName("构造校验")
    class Construction {

        @Test
        @DisplayName("objectMapper 为 null 时抛 NullPointerException")
        void nullObjectMapperRejected() {
            assertThatThrownBy(() -> new JacksonMcpArgumentBinder(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("objectMapper");
        }

        @Test
        @DisplayName("构造时不抛异常（Mock 实例足够）")
        void constructionSucceedsWithMock() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(mockObjectMapper);
            assertThat(binder).isNotNull();
        }
    }

    @Nested
    @DisplayName("null / 默认值 / 必填逻辑")
    class NullAndDefault {

        @Test
        @DisplayName("rawValue=null, 若必填且无 defaultValue，抛出 McpArgumentBindingException")
        void nullRequiredThrowsBindingException() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, true, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            assertThatThrownBy(() -> binder.bind(null, String.class, metadata))
                    .isInstanceOf(McpArgumentBindingException.class)
                    .hasMessageContaining("Missing required tool argument: name")
                    .extracting("argumentName").isEqualTo("name");
        }

        @Test
        @DisplayName("rawValue=null 但有 defaultValue 时返回 defaultValue 转换结果")
        void nullFallsBackToDefault() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, true, "fallback", null, null, List.of(), false);

            Object result = binder.bind(null, String.class, metadata);

            assertThat(result).isEqualTo("fallback");
        }

        @Test
        @DisplayName("rawValue=null, 非必填, 无 defaultValue → 返回 null")
        void nullOptionalReturnsNull() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            Object result = binder.bind(null, String.class, metadata);

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("rawValue=null, targetType 为 Optional 且非必填 → 返回 Optional.empty()")
        void nullTargetOptionalReturnsEmpty() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            Object result = binder.bind(null, Optional.class, metadata);

            assertThat(result).isInstanceOf(Optional.class);
            assertThat((Optional<?>) result).isEmpty();
        }

        @Test
        @DisplayName("Mock ObjectMapper 验证：rawValue=null 时仅调用 constructType，不调用 convertValue")
        void mockObjectMapperShortCircuitOnNull() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(mockObjectMapper);
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            given(mockObjectMapper.constructType(String.class)).willReturn(realMapper().constructType(String.class));
            Object result = binder.bind(null, String.class, metadata);

            assertThat(result).isNull();
            verify(mockObjectMapper).constructType(String.class);
            verify(mockObjectMapper, never()).convertValue(any(), any(JavaType.class));
        }
    }

    @Nested
    @DisplayName("类型转换")
    class Conversion {

        @Test
        @DisplayName("rawValue 非 null 时调用 ObjectMapper.convertValue 返回转换结果")
        void delegateConvertValueReturnsConverted() {
            ObjectMapper mapper = realMapper();
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(mapper);
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            Object result = binder.bind("42", Integer.class, metadata);

            assertThat(result).isInstanceOf(Integer.class).isEqualTo(42);
        }

        @Test
        @DisplayName("rawValue 非 null，convertValue 抛出 IllegalArgumentException 时翻译为 McpArgumentBindingException")
        void illegalArgumentExceptionIsTranslated() {
            JavaType stringType = realMapper().constructType(String.class);
            given(mockObjectMapper.constructType(String.class)).willReturn(stringType);
            given(mockObjectMapper.convertValue(any(), any(JavaType.class)))
                    .willThrow(new IllegalArgumentException("bad value"));
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(mockObjectMapper);
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            assertThatThrownBy(() -> binder.bind("abc", String.class, metadata))
                    .isInstanceOf(McpArgumentBindingException.class)
                    .hasMessageContaining("Invalid value for tool argument: name")
                    .hasCauseInstanceOf(IllegalArgumentException.class)
                    .extracting("argumentName").isEqualTo("name");
        }

        @Test
        @DisplayName("rawValue 非 null 走真实 Jackson 转换复杂对象")
        void complexTypeConvertedByRealJackson() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            Object result = binder.bind("hello", String.class, metadata);

            assertThat(result).isEqualTo("hello");
        }

        @Test
        @DisplayName("rawValue 非 null，目标类型为 Optional<T> 时 Jackson 转换并包装")
        void optionalTargetTypeConverted() {
            JacksonMcpArgumentBinder binder = new JacksonMcpArgumentBinder(realMapper());
            McpArgumentMetadata metadata = new McpArgumentMetadata(
                    "name", null, false, McpArgument.NO_DEFAULT, null, null, List.of(), false);

            Object result = binder.bind("hello", Optional.class, metadata);

            assertThat(result).isInstanceOf(Optional.class);
            @SuppressWarnings("unchecked")
            Optional<Object> typed = (Optional<Object>) result;
            assertThat(typed).contains("hello");
        }
    }

    private static ObjectMapper realMapper() {
        return JsonMapper.builder().build();
    }
}
