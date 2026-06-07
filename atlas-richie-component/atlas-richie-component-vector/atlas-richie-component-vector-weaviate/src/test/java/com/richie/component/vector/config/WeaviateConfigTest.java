package com.richie.component.vector.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.weaviate.WeaviateVectorStore;

import static org.assertj.core.api.Assertions.assertThat;

class WeaviateConfigTest {

    @Nested
    @DisplayName("default values")
    class DefaultValuesTests {

        @Test
        @DisplayName("objectClass should default to CustomClass")
        void objectClass_shouldDefaultToCustomClass() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getObjectClass()).isEqualTo("CustomClass");
        }

        @Test
        @DisplayName("consistencyLevel should default to QUORUM")
        void consistencyLevel_shouldDefaultToQuorum() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getConsistencyLevel()).isEqualTo(WeaviateVectorStore.ConsistentLevel.QUORUM);
        }

        @Test
        @DisplayName("filterMetadataFields should have default format")
        void filterMetadataFields_shouldHaveDefaultFormat() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getFilterMetadataFields()).isEqualTo("country:text,year:number");
        }

        @Test
        @DisplayName("scheme should be null when not set")
        void scheme_shouldBeNullWhenNotSet() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getScheme()).isNull();
        }

        @Test
        @DisplayName("host should be null when not set")
        void host_shouldBeNullWhenNotSet() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getHost()).isNull();
        }

        @Test
        @DisplayName("apiKey should be null when not set")
        void apiKey_shouldBeNullWhenNotSet() {
            WeaviateConfig config = new WeaviateConfig();
            assertThat(config.getApiKey()).isNull();
        }
    }

    @Nested
    @DisplayName("setters and getters")
    class SetterGetterTests {

        @Test
        @DisplayName("should store and retrieve scheme")
        void scheme_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setScheme("https");
            assertThat(config.getScheme()).isEqualTo("https");
        }

        @Test
        @DisplayName("should store and retrieve host")
        void host_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setHost("localhost");
            assertThat(config.getHost()).isEqualTo("localhost");
        }

        @Test
        @DisplayName("should store and retrieve apiKey")
        void apiKey_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setApiKey("secret-key");
            assertThat(config.getApiKey()).isEqualTo("secret-key");
        }

        @Test
        @DisplayName("should store and retrieve objectClass")
        void objectClass_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setObjectClass("MyClass");
            assertThat(config.getObjectClass()).isEqualTo("MyClass");
        }

        @Test
        @DisplayName("should store and retrieve consistencyLevel")
        void consistencyLevel_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setConsistencyLevel(WeaviateVectorStore.ConsistentLevel.ONE);
            assertThat(config.getConsistencyLevel()).isEqualTo(WeaviateVectorStore.ConsistentLevel.ONE);
        }

        @Test
        @DisplayName("should store and retrieve filterMetadataFields")
        void filterMetadataFields_shouldStoreAndRetrieve() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("category:text,count:number,tags:text");
            assertThat(config.getFilterMetadataFields()).isEqualTo("category:text,count:number,tags:text");
        }
    }

    @Nested
    @DisplayName("filterMetadataFields parsing format")
    class FilterMetadataFieldsFormatTests {

        @Test
        @DisplayName("should accept single field")
        void shouldAcceptSingleField() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("country:text");
            assertThat(config.getFilterMetadataFields()).isEqualTo("country:text");
        }

        @Test
        @DisplayName("should accept multiple fields")
        void shouldAcceptMultipleFields() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("a:text,b:number,c:text");
            assertThat(config.getFilterMetadataFields()).isEqualTo("a:text,b:number,c:text");
        }

        @Test
        @DisplayName("should accept empty string")
        void shouldAcceptEmptyString() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("");
            assertThat(config.getFilterMetadataFields()).isEqualTo("");
        }

        @Test
        @DisplayName("should accept text with only field name")
        void shouldAcceptFieldNameOnly() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("country");
            assertThat(config.getFilterMetadataFields()).isEqualTo("country");
        }

        @Test
        @DisplayName("should handle unknown type gracefully")
        void shouldHandleUnknownType() {
            WeaviateConfig config = new WeaviateConfig();
            config.setFilterMetadataFields("field:unknown");
            assertThat(config.getFilterMetadataFields()).isEqualTo("field:unknown");
        }
    }
}
