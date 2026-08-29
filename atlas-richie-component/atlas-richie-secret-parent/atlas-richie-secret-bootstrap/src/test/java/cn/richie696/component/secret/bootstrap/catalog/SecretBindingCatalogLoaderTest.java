/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.secret.bootstrap.catalog;

import cn.richie696.component.secret.api.exception.SecretConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretBindingCatalogLoaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void sameLogicalNameRequiresEveryBindingToDeclareShared() throws IOException {
        Path first = catalogDirectory("first", catalog("component-a", "property-a", false, 128));
        Path second = catalogDirectory("second", catalog("component-b", "property-b", false, 128));

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{first.toUri().toURL(), second.toUri().toURL()},
                null)) {
            assertThatThrownBy(() -> new SecretBindingCatalogLoader().load(classLoader))
                    .isInstanceOf(SecretConfigurationException.class)
                    .hasMessageContaining("shared=true");
        }
    }

    @Test
    void duplicatePropertyMustHaveIdenticalMetadata() throws IOException {
        Path first = catalogDirectory("first", catalog("component-a", "same-property", true, 128));
        Path second = catalogDirectory("second", catalog("component-b", "same-property", true, 256));

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{first.toUri().toURL(), second.toUri().toURL()},
                null)) {
            assertThatThrownBy(() -> new SecretBindingCatalogLoader().load(classLoader))
                    .isInstanceOf(SecretConfigurationException.class)
                    .hasMessageContaining("Conflicting Secret Binding");
        }
    }

    @Test
    void dynamicMapAndIndexPatternsRemainAConstrainedAllowlist() throws IOException {
        String content = """
                {
                  "schemaVersion": "1",
                  "component": "ai",
                  "bindings": [{
                    "property": "platform.component.ai.chat.{name}.api-keys[{index}]",
                    "logicalName": "ai.chat.api-key",
                    "kind": "API_KEY",
                    "exposure": "PROPERTY_SOURCE",
                    "refresh": "RECREATE_CLIENT",
                    "owner": "ai",
                    "maxLength": 512
                  }]
                }
                """;
        Path root = catalogDirectory("dynamic", content);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{root.toUri().toURL()}, null)) {
            SecretBindingCatalogSet bindings = new SecretBindingCatalogLoader().load(classLoader);

            assertThat(bindings.propertySourceBinding(
                    "platform.component.ai.chat.openai-4.api-keys[0]")).isPresent();
            assertThat(bindings.propertySourceBinding(
                    "platform.component.ai.chat.openai.extra.api-keys[0]")).isEmpty();
            assertThat(bindings.propertySourceBinding(
                    "platform.component.ai.chat.openai-4.api-keys[x]")).isEmpty();
        }
    }

    @Test
    void dynamicPatternCannotDeclareAnUnboundedRequiredCondition() throws IOException {
        String content = """
                {
                  "schemaVersion": "1",
                  "component": "ai",
                  "bindings": [{
                    "property": "platform.component.ai.chat.{name}.api-key",
                    "logicalName": "ai.chat.api-key",
                    "kind": "API_KEY",
                    "exposure": "PROPERTY_SOURCE",
                    "requiredWhen": {"property":"platform.component.ai.enabled","in":["true"]},
                    "refresh": "RECREATE_CLIENT",
                    "owner": "ai"
                  }]
                }
                """;
        Path root = catalogDirectory("dynamic-required", content);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{root.toUri().toURL()}, null)) {
            assertThatThrownBy(() -> new SecretBindingCatalogLoader().load(classLoader))
                    .isInstanceOf(SecretConfigurationException.class)
                    .hasMessageContaining("requiredWhen");
        }
    }

    @Test
    void catalogCannotAllowlistAForbiddenControlProperty() throws IOException {
        String content = """
                {
                  "schemaVersion": "1",
                  "component": "malformed-component",
                  "bindings": [{
                    "property": "server.port",
                    "logicalName": "control.server-port",
                    "kind": "TOKEN_SECRET",
                    "exposure": "PROPERTY_SOURCE",
                    "refresh": "STATIC",
                    "owner": "malformed-component"
                  }]
                }
                """;
        Path root = catalogDirectory("forbidden-control-property", content);

        try (URLClassLoader classLoader = new URLClassLoader(
                new java.net.URL[]{root.toUri().toURL()}, null)) {
            assertThatThrownBy(() -> new SecretBindingCatalogLoader().load(classLoader))
                    .isInstanceOf(SecretConfigurationException.class)
                    .hasMessageContaining("forbidden control property")
                    .hasMessageContaining("server.port");
        }
    }

    private Path catalogDirectory(String name, String content) throws IOException {
        Path root = tempDirectory.resolve(name);
        Path catalog = root.resolve(SecretBindingCatalogLoader.RESOURCE_PATH);
        Files.createDirectories(catalog.getParent());
        Files.writeString(catalog, content);
        return root;
    }

    private String catalog(String component, String propertySuffix, boolean shared, int maxLength) {
        return """
                {
                  "schemaVersion": "1",
                  "component": "%s",
                  "bindings": [{
                    "property": "platform.component.test.%s",
                    "logicalName": "shared.credential",
                    "kind": "TOKEN_SECRET",
                    "exposure": "PROPERTY_SOURCE",
                    "refresh": "STATIC",
                    "owner": "test",
                    "shared": %s,
                    "maxLength": %d
                  }]
                }
                """.formatted(component, propertySuffix, shared, maxLength);
    }
}
