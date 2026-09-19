/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies provider configuration metadata after the provider modules have
 * been compiled. This belongs in the final integration-tests module because
 * the core module is built before its sibling provider modules in the Maven
 * reactor and therefore cannot inspect their build output safely.
 */
class VectorProviderConfigurationMetadataTest {

    private static final String[] PROVIDER_MODULES = {
            "atlas-richie-vector-milvus",
            "atlas-richie-vector-weaviate",
            "atlas-richie-vector-qdrant",
            "atlas-richie-vector-redis",
            "atlas-richie-vector-postgresql",
            "atlas-richie-vector-neo4j",
            "atlas-richie-vector-vikingdb"
    };

    @Test
    void everyProviderJarExposesMetadataWithAtLeastOneProviderOwnedKey() throws Exception {
        for (String module : PROVIDER_MODULES) {
            Set<String> names = collectPropertyNames(module);
            assertThat(names)
                    .as("module %s must expose at least one provider-owned property", module)
                    .isNotEmpty();
            assertThat(names)
                    .as("module %s metadata must include provider namespace", module)
                    .anyMatch(n -> n.startsWith("platform.component.vector."));
        }
    }

    @Test
    void providerMetadataNeverEmbedsCredentialLikeSamples() throws Exception {
        for (String module : PROVIDER_MODULES) {
            String raw = Files.readString(metadataPath(module)).toLowerCase();
            assertThat(raw)
                    .as("module %s metadata must not include sample credential literals", module)
                    .doesNotContain("ak=")
                    .doesNotContain("sk=")
                    .doesNotContain("password=secret")
                    .doesNotContain("api-key=secret");
        }
    }

    private static Set<String> collectPropertyNames(String module) throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.newInputStream(metadataPath(module)));
        Set<String> names = new HashSet<>();
        Iterator<JsonNode> properties = root.path("properties").elements();
        while (properties.hasNext()) {
            names.add(properties.next().path("name").asText());
        }
        return names;
    }

    private static Path metadataPath(String module) throws IOException {
        Path workspace = locateWorkspaceRoot();
        Path target = workspace.resolve(module)
                .resolve("target/classes/META-INF/spring-configuration-metadata.json");
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("missing metadata: " + target);
        }
        return target;
    }

    private static Path locateWorkspaceRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            try (Stream<Path> stream = Files.list(current)) {
                if (stream.map(Path::getFileName)
                        .map(Path::toString)
                        .anyMatch(name -> name.startsWith("atlas-richie-vector-"))) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException("could not locate vector module workspace");
    }
}
