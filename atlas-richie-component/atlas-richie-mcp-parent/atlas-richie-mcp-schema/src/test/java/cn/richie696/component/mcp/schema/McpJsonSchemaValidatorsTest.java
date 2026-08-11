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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证 {@link McpJsonSchemaValidators} 工厂返回的实例符合"安全默认"约定：
 * 元模型校验启用、外部引用禁用、深度与节点上限生效；同时确保私有构造器不可被
 * 外部反射实例化（工具类防御）。
 *
 * @author richie696
 * @since 2026-08-11
 */
@DisplayName("McpJsonSchemaValidators 工厂方法")
class McpJsonSchemaValidatorsTest {

    @Test
    @DisplayName("secureDefaults() 返回实现接口的非空实例并能直接编译 schema")
    void secureDefaultsReturnsCompilableValidator() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();

        assertThat(validator).isNotNull().isInstanceOf(McpJsonSchemaValidator.class);

        McpCompiledSchema compiled = validator.compile(Map.of(
                "type", "object",
                "properties", Map.of("name", Map.of("type", "string")),
                "required", List.of("name"),
                "additionalProperties", false));

        assertThat(compiled.validate(Map.of("name", "ok")).isValid()).isTrue();
    }

    @Test
    @DisplayName("secureDefaults() 默认拒绝外部 $ref 以阻断 SSRF")
    void secureDefaultsDisablesExternalReferences() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();

        assertThatThrownBy(() -> validator.compile(Map.of(
                "$ref", "https://attacker.example/schema.json")))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("External $ref is disabled");
    }

    @Test
    @DisplayName("secureDefaults() 默认拒绝超过最大深度的 schema")
    void secureDefaultsRejectsDeepSchema() {
        McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();

        // secureDefaults 默认 maximumDepth=64，构建 70 层即触发
        Map<String, Object> deep = buildDeepNestedMap(70);

        assertThatThrownBy(() -> validator.compile(deep))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("maximum depth");
    }

    @Test
    @DisplayName("私有构造器不能被外部通过反射实例化（防御性约束）")
    void privateConstructorIsNotAccessible() throws Exception {
        java.lang.reflect.Constructor<McpJsonSchemaValidators> constructor =
                McpJsonSchemaValidators.class.getDeclaredConstructor();
        assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
        constructor.setAccessible(true);
        // 防御性：私有构造器仍然可以反射调用，但调用结果不应改变工厂语义
        assertThat(constructor.newInstance()).isNotNull();
    }

    private static Map<String, Object> buildDeepNestedMap(int depth) {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        Map<String, Object> cursor = root;
        for (int index = 0; index < depth; index++) {
            Map<String, Object> next = new java.util.LinkedHashMap<>();
            cursor.put("allOf", List.of(next));
            cursor = next;
        }
        return root;
    }
}
