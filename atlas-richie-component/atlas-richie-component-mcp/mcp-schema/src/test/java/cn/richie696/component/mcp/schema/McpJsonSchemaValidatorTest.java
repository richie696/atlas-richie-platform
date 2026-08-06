package cn.richie696.component.mcp.schema;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpJsonSchemaValidatorTest {
    private final McpJsonSchemaValidator validator = McpJsonSchemaValidators.secureDefaults();

    @Test
    void validatesDraft202012RequiredTypesAndFormats() {
        McpCompiledSchema schema = validator.compile(Map.of(
                "$schema", "https://json-schema.org/draft/2020-12/schema",
                "type", "object",
                "properties", Map.of(
                        "name", Map.of("type", "string", "minLength", 2),
                        "birthday", Map.of("type", "string", "format", "date")),
                "required", List.of("name", "birthday"),
                "additionalProperties", false));

        assertThat(schema.validate(Map.of("name", "Ada", "birthday", "1815-12-10")).isValid())
                .isTrue();
        McpSchemaValidationResult invalid =
                schema.validate(Map.of("name", "A", "birthday", "not-a-date", "extra", true));
        assertThat(invalid.isValid()).isFalse();
        assertThat(invalid.violations()).extracting(McpSchemaViolation::keyword)
                .contains("minLength", "format", "additionalProperties");
    }

    @Test
    void supportsDraft202012DefsAndUnevaluatedProperties() {
        McpCompiledSchema schema = validator.compile(Map.of(
                "$defs", Map.of("identifier", Map.of(
                        "type", "string",
                        "pattern", "^[A-Z]-[0-9]+$")),
                "type", "object",
                "properties", Map.of("id", Map.of("$ref", "#/$defs/identifier")),
                "required", List.of("id"),
                "unevaluatedProperties", false));

        assertThat(schema.validate(Map.of("id", "C-42")).isValid()).isTrue();
        assertThat(schema.validate(Map.of("id", "bad", "extra", true)).violations())
                .extracting(McpSchemaViolation::keyword)
                .contains("pattern", "unevaluatedProperties");
    }

    @Test
    void rejectsInvalidSchemaDefinitionBeforeRuntime() {
        assertThatThrownBy(() -> validator.compile(Map.of("type", "not-a-json-schema-type")))
                .isInstanceOfSatisfying(McpSchemaDefinitionException.class,
                        exception -> assertThat(exception.violations()).isNotEmpty());
    }

    @Test
    void rejectsExternalReferencesByDefault() {
        assertThatThrownBy(() -> validator.compile(Map.of(
                "$ref", "https://attacker.example/schema.json")))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("External $ref is disabled");
        assertThatThrownBy(() -> validator.compile(Map.of(
                "$dynamicRef", "https://attacker.example/dynamic.json")))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("External $dynamicRef is disabled");
    }

    @Test
    void rejectsCyclicObjectGraphsAndResourceExhaustionShapes() {
        Map<String, Object> cyclic = new LinkedHashMap<>();
        cyclic.put("type", "object");
        cyclic.put("properties", cyclic);

        assertThatThrownBy(() -> validator.compile(cyclic))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("cycle");

        Map<String, Object> deep = new LinkedHashMap<>();
        Map<String, Object> cursor = deep;
        for (int index = 0; index < 70; index++) {
            Map<String, Object> next = new LinkedHashMap<>();
            cursor.put("allOf", List.of(next));
            cursor = next;
        }
        assertThatThrownBy(() -> validator.compile(deep))
                .isInstanceOf(McpSchemaDefinitionException.class)
                .hasMessageContaining("maximum depth");
    }
}
