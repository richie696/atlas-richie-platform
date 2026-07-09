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
package com.richie.component.vector.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QdRantConfigTest {

    @Test
    void defaults_shouldHaveCorrectInitialValues() {
        QdRantConfig config = new QdRantConfig();

        assertEquals(6333, config.getPort());
        assertEquals("documents", config.getCollection());
        assertFalse(config.isUseTransportLayerSecurity());
        assertFalse(config.isInitializeSchema());
        assertNull(config.getHost());
    }

    @Test
    void setters_shouldUpdateFieldValues() {
        QdRantConfig config = new QdRantConfig();

        config.setHost("localhost");
        config.setPort(6334);
        config.setUseTransportLayerSecurity(true);
        config.setCollection("my-collection");
        config.setInitializeSchema(true);

        assertEquals("localhost", config.getHost());
        assertEquals(6334, config.getPort());
        assertTrue(config.isUseTransportLayerSecurity());
        assertEquals("my-collection", config.getCollection());
        assertTrue(config.isInitializeSchema());
    }

    @Test
    void toString_shouldIncludeAllFields() {
        QdRantConfig config = new QdRantConfig();
        config.setHost("qdrant.example.com");
        config.setPort(6334);
        config.setUseTransportLayerSecurity(true);
        config.setCollection("test-col");
        config.setInitializeSchema(true);

        String str = config.toString();

        assertContains(str, "qdrant.example.com");
        assertContains(str, "6334");
        assertContains(str, "test-col");
        assertContains(str, "useTransportLayerSecurity=true");
        assertContains(str, "initializeSchema=true");
    }

    @Test
    void equals_and_hashCode_shouldBeLombokGenerated() {
        QdRantConfig config1 = new QdRantConfig();
        config1.setHost("localhost");
        config1.setPort(6333);

        QdRantConfig config2 = new QdRantConfig();
        config2.setHost("localhost");
        config2.setPort(6333);

        QdRantConfig config3 = new QdRantConfig();
        config3.setHost("other-host");
        config3.setPort(6333);

        // Same values should be equal
        assertEquals(config1, config2);
        assertEquals(config1.hashCode(), config2.hashCode());
        assertNotEquals(config1, config3);
    }

    @Test
    void allFieldsCanBeSetAndRetrieved() {
        QdRantConfig config = new QdRantConfig();
        config.setHost("192.168.1.100");
        config.setPort(6335);
        config.setUseTransportLayerSecurity(true);
        config.setCollection("custom-collection");
        config.setInitializeSchema(true);

        assertEquals("192.168.1.100", config.getHost());
        assertEquals(6335, config.getPort());
        assertTrue(config.isUseTransportLayerSecurity());
        assertEquals("custom-collection", config.getCollection());
        assertTrue(config.isInitializeSchema());
    }

    private static void assertContains(String actual, String expected) {
        assertTrue(actual.contains(expected),
                "Expected string to contain '" + expected + "' but was: " + actual);
    }
}
