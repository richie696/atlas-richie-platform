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
package com.richie.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ThirdPartyClientRegisterDTO}.
 */
@DisplayName("ThirdPartyClientRegisterDTO Tests")
class ThirdPartyClientRegisterDTOTest {

    @Nested
    @DisplayName("Default Constructor")
    class DefaultConstructor {

        @Test
        @DisplayName("should have null clientName by default")
        void shouldHaveNullClientNameByDefault() {
            ThirdPartyClientRegisterDTO dto = new ThirdPartyClientRegisterDTO();
            assertThat(dto.getClientName()).isNull();
        }
    }

    @Nested
    @DisplayName("Setter and Getter")
    class SetterAndGetter {

        @Test
        @DisplayName("should set and get clientName")
        void shouldSetAndGetClientName() {
            ThirdPartyClientRegisterDTO dto = new ThirdPartyClientRegisterDTO();
            dto.setClientName("partner-system");

            assertThat(dto.getClientName()).isEqualTo("partner-system");
        }

        @Test
        @DisplayName("should allow overwriting clientName")
        void shouldAllowOverwritingClientName() {
            ThirdPartyClientRegisterDTO dto = new ThirdPartyClientRegisterDTO();
            dto.setClientName("first");
            dto.setClientName("second");

            assertThat(dto.getClientName()).isEqualTo("second");
        }
    }

    @Nested
    @DisplayName("Equals and HashCode")
    class EqualsAndHashCode {

        @Test
        @DisplayName("should be equal when clientName matches")
        void shouldBeEqualWhenClientNameMatches() {
            ThirdPartyClientRegisterDTO d1 = new ThirdPartyClientRegisterDTO();
            d1.setClientName("c");
            ThirdPartyClientRegisterDTO d2 = new ThirdPartyClientRegisterDTO();
            d2.setClientName("c");

            assertThat(d1).isEqualTo(d2);
            assertThat(d1.hashCode()).isEqualTo(d2.hashCode());
        }

        @Test
        @DisplayName("should not be equal when clientName differs")
        void shouldNotBeEqualWhenClientNameDiffers() {
            ThirdPartyClientRegisterDTO d1 = new ThirdPartyClientRegisterDTO();
            d1.setClientName("c1");
            ThirdPartyClientRegisterDTO d2 = new ThirdPartyClientRegisterDTO();
            d2.setClientName("c2");

            assertThat(d1).isNotEqualTo(d2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTests {

        @Test
        @DisplayName("should contain clientName field name in toString output")
        void shouldContainClientNameFieldNameInToStringOutput() {
            ThirdPartyClientRegisterDTO dto = new ThirdPartyClientRegisterDTO();
            dto.setClientName("partner-system");

            assertThat(dto.toString()).contains("clientName");
        }
    }

    @Nested
    @DisplayName("JsonProperty Mapping")
    class JsonPropertyMapping {

        @Test
        @DisplayName("should map clientName field to snake_case client_name")
        void shouldMapClientNameFieldToSnakeCase() throws NoSuchFieldException {
            Field field = ThirdPartyClientRegisterDTO.class.getDeclaredField("clientName");
            JsonProperty annotation = field.getAnnotation(JsonProperty.class);

            assertThat(annotation).isNotNull();
            assertThat(annotation.value()).isEqualTo("client_name");
        }

        @Test
        @DisplayName("should serialize to snake_case JSON key")
        void shouldSerializeToSnakeCaseJsonKey() throws Exception {
            ThirdPartyClientRegisterDTO dto = new ThirdPartyClientRegisterDTO();
            dto.setClientName("partner-system");

            String json = new ObjectMapper().writeValueAsString(dto);

            assertThat(json).contains("\"client_name\":\"partner-system\"");
        }

        @Test
        @DisplayName("should deserialize from snake_case JSON key")
        void shouldDeserializeFromSnakeCaseJsonKey() throws Exception {
            String json = "{\"client_name\":\"partner-system\"}";

            ThirdPartyClientRegisterDTO dto = new ObjectMapper().readValue(json, ThirdPartyClientRegisterDTO.class);

            assertThat(dto.getClientName()).isEqualTo("partner-system");
        }
    }
}
