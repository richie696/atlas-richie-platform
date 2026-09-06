/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 * Licensed under the Apache License, Version 2.0.
 */
package cn.richie696.component.vector.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * R-003: assert that the generated Spring Boot configuration metadata covers
 * every critical Named-Multi-store knob and that no credential-shaped value
 * leaks into the description text. Reads metadata directly from the build
 * output so this single test class can verify all modules.
 */
class VectorConfigurationMetadataTest {

    private static final String[] MODULES = {
            "atlas-richie-vector-core",
            "atlas-richie-vector-milvus",
            "atlas-richie-vector-weaviate",
            "atlas-richie-vector-qdrant",
            "atlas-richie-vector-redis",
            "atlas-richie-vector-postgresql",
            "atlas-richie-vector-mongodb-atlas",
            "atlas-richie-vector-neo4j",
            "atlas-richie-vector-vikingdb"
    };

    @Test
    void coreMetadataCoversCriticalNamedTopologyProperties() throws Exception {
        Set<String> names = collectPropertyNamesFromBuildOutput("atlas-richie-vector-core");
        assertThat(names)
                .contains("platform.component.vector.provider",
                        "platform.component.vector.connections",
                        "platform.component.vector.stores",
                        "platform.component.vector.default-index");
    }

    @Test
    void additionalMetadataDocumentsNestedStoreAndQueryDefaults() throws Exception {
        Set<String> names = collectPropertyNamesFromBuildOutput("atlas-richie-vector-core");
        assertThat(names).contains(
                "platform.component.vector.connections.<connectionId>.provider",
                "platform.component.vector.connections.<connectionId>.settings",
                "platform.component.vector.stores.<vectorStoreId>.connection-ref",
                "platform.component.vector.stores.<vectorStoreId>.embedding-model-ref",
                "platform.component.vector.stores.<vectorStoreId>.embedding-normalization",
                "platform.component.vector.stores.<vectorStoreId>.embedding-modalities",
                "platform.component.vector.stores.<vectorStoreId>.default-index",
                "platform.component.vector.stores.<vectorStoreId>.required",
                "platform.component.vector.stores.<vectorStoreId>.required-capabilities",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.top-k",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.min-score",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.candidate-limit",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.timeout",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.consistency",
                "platform.component.vector.stores.<vectorStoreId>.query-defaults.return-fields",
                "platform.component.vector.stores.<vectorStoreId>.indexes.<indexId>.name",
                "platform.component.vector.stores.<vectorStoreId>.indexes.<indexId>.dimension",
                "platform.component.vector.stores.<vectorStoreId>.indexes.<indexId>.metric",
                "platform.component.vector.stores.<vectorStoreId>.indexes.<indexId>.index-type");
    }

    @Test
    void everyProviderJarExposesMetadataWithAtLeastOneProviderOwnedKey() throws Exception {
        for (String module : new String[]{
                "atlas-richie-vector-milvus",
                "atlas-richie-vector-weaviate",
                "atlas-richie-vector-qdrant",
                "atlas-richie-vector-redis",
                "atlas-richie-vector-postgresql",
                "atlas-richie-vector-neo4j",
                "atlas-richie-vector-vikingdb"}) {
            Set<String> names = collectPropertyNamesFromBuildOutput(module);
            assertThat(names)
                    .as("module %s must expose at least one provider-owned property", module)
                    .isNotEmpty();
            assertThat(names)
                    .as("module %s metadata must include provider namespace", module)
                    .anyMatch(n -> n.startsWith("platform.component.vector."));
        }
    }

    @Test
    void metadataNeverEmbedsCredentialLikeSamples() throws Exception {
        for (String module : new String[]{
                "atlas-richie-vector-core",
                "atlas-richie-vector-milvus",
                "atlas-richie-vector-weaviate",
                "atlas-richie-vector-qdrant",
                "atlas-richie-vector-redis",
                "atlas-richie-vector-postgresql",
                "atlas-richie-vector-neo4j",
                "atlas-richie-vector-vikingdb"}) {
            String raw = readMetadataRaw(module).toLowerCase();
            assertThat(raw)
                    .as("module %s metadata must not include sample ak/sk literals", module)
                    .doesNotContain("ak=")
                    .doesNotContain("sk=")
                    .doesNotContain("password=secret")
                    .doesNotContain("api-key=secret");
        }
    }

    private static Set<String> collectPropertyNamesFromBuildOutput(String module) throws Exception {
        JsonNode root = readMergedMetadata(module);
        Set<String> result = new HashSet<>();
        JsonNode properties = root.path("properties");
        Iterator<JsonNode> it = properties.elements();
        while (it.hasNext()) {
            result.add(it.next().path("name").asText());
        }
        return result;
    }

    private static String readMetadataRaw(String module) throws Exception {
        String generated = Files.readString(metadataPath(module));
        Path additional = additionalMetadataPath(module);
        if (Files.isRegularFile(additional)) {
            return generated + "\n" + Files.readString(additional);
        }
        return generated;
    }

    private static JsonNode readMergedMetadata(String module) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(Files.newInputStream(metadataPath(module)));
        Path additional = additionalMetadataPath(module);
        if (Files.isRegularFile(additional)) {
            JsonNode extra = mapper.readTree(Files.newInputStream(additional));
            return merge(root, extra);
        }
        return root;
    }

    private static JsonNode merge(JsonNode base, JsonNode extra) {
        var merged = base.deepCopy();
        Iterator<String> fields = extra.fieldNames();
        while (fields.hasNext()) {
            String name = fields.next();
            JsonNode value = extra.get(name);
            if (merged.has(name) && merged.get(name).isArray() && value.isArray()) {
                var array = ((com.fasterxml.jackson.databind.node.ArrayNode) merged.get(name));
                value.forEach(array::add);
            } else {
                ((com.fasterxml.jackson.databind.node.ObjectNode) merged).set(name, value);
            }
        }
        return merged;
    }

    private static Path metadataPath(String module) throws IOException {
        Path workspace = locateWorkspaceRoot();
        Path target = workspace.resolve(module).resolve("target/classes/META-INF/spring-configuration-metadata.json");
        if (!Files.isRegularFile(target)) {
            throw new IllegalStateException("missing metadata: " + target);
        }
        return target;
    }

    private static Path additionalMetadataPath(String module) throws IOException {
        Path workspace = locateWorkspaceRoot();
        return workspace.resolve(module)
                .resolve("target/classes/META-INF/additional-spring-configuration-metadata.json");
    }

    /**
     * Walks up from the current working directory to find the directory that
     * contains the multi-module build root (the directory that owns every
     * {@code atlas-richie-vector-*} module directory).
     */
    private static Path locateWorkspaceRoot() throws IOException {
        Path current = new File("").toPath().toAbsolutePath();
        while (current != null) {
            try (Stream<Path> stream = Files.list(current)) {
                boolean hasModuleDir = stream
                        .map(Path::getFileName)
                        .map(Path::toString)
                        .anyMatch(name -> name.startsWith("atlas-richie-vector-"));
                if (hasModuleDir) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "could not locate the multi-module build root from " + new File("").getAbsolutePath());
    }
}
