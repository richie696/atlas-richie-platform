/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.gateway.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link EncryptTypeEnum}.
 */
@DisplayName("EncryptTypeEnum Tests")
class EncryptTypeEnumTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have MD5 enum value")
        void shouldHaveMd5EnumValue() {
            assertThat(EncryptTypeEnum.MD5).isNotNull();
        }

        @Test
        @DisplayName("should have RSA enum value")
        void shouldHaveRsaEnumValue() {
            assertThat(EncryptTypeEnum.RSA).isNotNull();
        }

        @Test
        @DisplayName("should have SM2 enum value")
        void shouldHaveSm2EnumValue() {
            assertThat(EncryptTypeEnum.SM2).isNotNull();
        }

        @Test
        @DisplayName("should have exactly 3 enum values")
        void shouldHaveExactlyThreeEnumValues() {
            assertThat(EncryptTypeEnum.values()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("ValueOf")
    class ValueOfTests {

        @Test
        @DisplayName("valueOf should return MD5 for MD5")
        void valueOfShouldReturnMd5ForMd5() {
            assertThat(EncryptTypeEnum.valueOf("MD5")).isEqualTo(EncryptTypeEnum.MD5);
        }

        @Test
        @DisplayName("valueOf should return RSA for RSA")
        void valueOfShouldReturnRsaForRsa() {
            assertThat(EncryptTypeEnum.valueOf("RSA")).isEqualTo(EncryptTypeEnum.RSA);
        }

        @Test
        @DisplayName("valueOf should return SM2 for SM2")
        void valueOfShouldReturnSm2ForSm2() {
            assertThat(EncryptTypeEnum.valueOf("SM2")).isEqualTo(EncryptTypeEnum.SM2);
        }

        @Test
        @DisplayName("valueOf should throw IllegalArgumentException for invalid name")
        void valueOfShouldThrowIllegalArgumentExceptionForInvalidName() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> EncryptTypeEnum.valueOf("UNKNOWN"));
        }
    }

    @Nested
    @DisplayName("Name and ToString")
    class NameAndToStringTests {

        @Test
        @DisplayName("MD5 name should be MD5")
        void md5NameShouldBeMd5() {
            assertThat(EncryptTypeEnum.MD5.name()).isEqualTo("MD5");
        }

        @Test
        @DisplayName("RSA name should be RSA")
        void rsaNameShouldBeRsa() {
            assertThat(EncryptTypeEnum.RSA.name()).isEqualTo("RSA");
        }

        @Test
        @DisplayName("SM2 name should be SM2")
        void sm2NameShouldBeSm2() {
            assertThat(EncryptTypeEnum.SM2.name()).isEqualTo("SM2");
        }

        @Test
        @DisplayName("toString should return enum name")
        void toStringShouldReturnEnumName() {
            assertThat(EncryptTypeEnum.MD5.toString()).isEqualTo("MD5");
            assertThat(EncryptTypeEnum.RSA.toString()).isEqualTo("RSA");
            assertThat(EncryptTypeEnum.SM2.toString()).isEqualTo("SM2");
        }
    }

    @Nested
    @DisplayName("Ordinal")
    class OrdinalTests {

        @Test
        @DisplayName("MD5 should have ordinal 0")
        void md5ShouldHaveOrdinal0() {
            assertThat(EncryptTypeEnum.MD5.ordinal()).isEqualTo(0);
        }

        @Test
        @DisplayName("RSA should have ordinal 1")
        void rsaShouldHaveOrdinal1() {
            assertThat(EncryptTypeEnum.RSA.ordinal()).isEqualTo(1);
        }

        @Test
        @DisplayName("SM2 should have ordinal 2")
        void sm2ShouldHaveOrdinal2() {
            assertThat(EncryptTypeEnum.SM2.ordinal()).isEqualTo(2);
        }
    }
}
