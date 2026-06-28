package com.richie.component.nats.strategy;

import com.richie.component.nats.exception.NatsSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link JacksonNatsMessageSerializer} 单元测试
 */
class JacksonNatsMessageSerializerTest {

    private JacksonNatsMessageSerializer serializer;

    @BeforeEach
    void setUp() {
        serializer = new JacksonNatsMessageSerializer();
    }

    @Test
    void serialize_shouldReturnJsonBytes() {
        Map<String, String> payload = Map.of("key", "value");
        byte[] bytes = serializer.serialize(payload);
        assertThat(bytes).isNotNull();
        assertThat(new String(bytes)).contains("\"key\"").contains("\"value\"");
    }

    @Test
    void deserialize_shouldReturnOriginalObject() {
        Map<String, String> payload = Map.of("hello", "world");
        byte[] bytes = serializer.serialize(payload);
        @SuppressWarnings("unchecked")
        Map<String, String> result = serializer.deserialize(bytes, Map.class);
        assertThat(result).containsEntry("hello", "world");
    }

    @Test
    void serialize_stringPayload_shouldWork() {
        byte[] bytes = serializer.serialize("test-message");
        assertThat(bytes).isNotNull();
        assertThat(new String(bytes)).contains("test-message");
    }

    @Test
    void deserialize_invalidJson_shouldThrowNatsSerializationException() {
        byte[] invalid = "not-json{{{".getBytes();
        assertThatThrownBy(() -> serializer.deserialize(invalid, Map.class))
                .isInstanceOf(NatsSerializationException.class)
                .hasMessageContaining("Deserialized");
    }

    @Test
    void roundTrip_complexObject_shouldPreserveData() {
        TestPayload original = new TestPayload();
        original.setName("test");
        original.setCount(42);

        byte[] bytes = serializer.serialize(original);
        TestPayload deserialized = serializer.deserialize(bytes, TestPayload.class);

        assertThat(deserialized.getName()).isEqualTo("test");
        assertThat(deserialized.getCount()).isEqualTo(42);
    }

    public static class TestPayload {
        private String name;
        private int count;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
    }
}
