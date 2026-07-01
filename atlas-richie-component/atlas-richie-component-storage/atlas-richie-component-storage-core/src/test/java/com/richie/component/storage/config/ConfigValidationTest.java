package com.richie.component.storage.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ConfigValidation} 单元测试
 */
@DisplayName("ConfigValidation 测试")
class ConfigValidationTest {

    @Nested
    @DisplayName("requireNonNull")
    class RequireNonNull {

        @Test
        @DisplayName("非 null 通过")
        void nonNull_passes() {
            assertThatCode(() -> ConfigValidation.requireNonNull(new Object(), "field"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 抛 IllegalArgumentException，含字段名")
        void null_throws() {
            assertThatThrownBy(() -> ConfigValidation.requireNonNull(null, "endpoint"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("endpoint")
                    .hasMessageContaining("不能为空");
        }
    }

    @Nested
    @DisplayName("requireNonBlank")
    class RequireNonBlank {

        @Test
        @DisplayName("正常字符串通过")
        void normalString_passes() {
            assertThatCode(() -> ConfigValidation.requireNonBlank("value", "field"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("空字符串抛异常")
        void empty_throws() {
            assertThatThrownBy(() -> ConfigValidation.requireNonBlank("", "bucketName"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("bucketName");
        }

        @Test
        @DisplayName("null 抛异常")
        void null_throws() {
            assertThatThrownBy(() -> ConfigValidation.requireNonBlank(null, "accessKeyId"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("accessKeyId");
        }

        @Test
        @DisplayName("纯空白字符串抛异常")
        void whitespace_throws() {
            assertThatThrownBy(() -> ConfigValidation.requireNonBlank("   ", "host"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("host");
        }

        @Test
        @DisplayName("含前后空白但非空的字符串通过")
        void paddedNonBlank_passes() {
            assertThatCode(() -> ConfigValidation.requireNonBlank("  value  ", "field"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null 字段名抛 NPE")
        void nullName_throws() {
            assertThatThrownBy(() -> ConfigValidation.requireNonBlank("value", null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}