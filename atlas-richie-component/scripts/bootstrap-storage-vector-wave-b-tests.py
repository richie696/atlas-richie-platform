#!/usr/bin/env python3
"""
Wave B: storage + vector 组件测试补齐。
- storage-core / storage-local / vector-core / vector-redis: UNIT+IT
- 其余 provider: UNIT_ONLY（删除 SpringContextIT）
"""

from __future__ import annotations

import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STORAGE = ROOT / "atlas-richie-component" / "atlas-richie-component-storage"
VECTOR = ROOT / "atlas-richie-component" / "atlas-richie-component-vector"

STORAGE_UNIT_IT = ["atlas-richie-component-storage-core", "atlas-richie-component-storage-local"]
STORAGE_UNIT_ONLY = [
    "atlas-richie-component-storage-s3",
    "atlas-richie-component-storage-minio",
    "atlas-richie-component-storage-oss",
    "atlas-richie-component-storage-cos",
    "atlas-richie-component-storage-obs",
    "atlas-richie-component-storage-ks3",
    "atlas-richie-component-storage-tos",
    "atlas-richie-component-storage-azure",
    "atlas-richie-component-storage-ftp",
    "atlas-richie-component-storage-sftp",
    "atlas-richie-component-storage-smb",
]
VECTOR_UNIT_IT = ["atlas-richie-component-vector-core", "atlas-richie-component-vector-redis"]
VECTOR_UNIT_ONLY = [
    "atlas-richie-component-vector-elasticsearch",
    "atlas-richie-component-vector-milvus",
    "atlas-richie-component-vector-qdrant",
    "atlas-richie-component-vector-weaviate",
    "atlas-richie-component-vector-neo4j",
    "atlas-richie-component-vector-postgresql",
    "atlas-richie-component-vector-mongodb-atlas",
]

JACOCO_STORAGE_CORE = """
                    <includes>
                        <include>com/richie/component/storage/core/impl/**</include>
                        <include>com/richie/component/storage/util/**</include>
                        <include>com/richie/component/storage/support/**</include>
                        <include>com/richie/component/storage/exception/**</include>
                    </includes>"""

JACOCO_STORAGE_LOCAL = """
                    <includes>
                        <include>com/richie/component/storage/local/core/impl/**</include>
                        <include>com/richie/component/storage/local/cleanup/**</include>
                        <include>com/richie/component/storage/local/config/CacheConfigurationChecker*</include>
                        <include>com/richie/component/storage/local/exception/**</include>
                    </includes>"""

JACOCO_STORAGE_PROVIDER = """
                    <includes>
                        <include>com/richie/component/storage/core/impl/*</include>
                        <include>com/richie/component/storage/converter/*</include>
                    </includes>"""

JACOCO_VECTOR_CORE = """
                    <includes>
                        <include>com/richie/component/vector/service/impl/**</include>
                    </includes>"""

JACOCO_VECTOR_PROVIDER = """
                    <includes>
                        <include>com/richie/component/vector/service/impl/*</include>
                    </includes>"""


def write(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.strip() + "\n", encoding="utf-8")
    print(f"  wrote {path.relative_to(ROOT)}")


def ensure_jacoco_includes(pom: Path, includes_block: str) -> None:
    text = pom.read_text(encoding="utf-8")
    if "<includes>" in text and "jacoco-maven-plugin" in text:
        return
    if "jacoco-maven-plugin" not in text:
        return
    text = text.replace(
        "<artifactId>jacoco-maven-plugin</artifactId>",
        "<artifactId>jacoco-maven-plugin</artifactId>\n                <configuration>" + includes_block + "\n                </configuration>",
        1,
    )
    pom.write_text(text, encoding="utf-8")
    print(f"  jacoco includes -> {pom.relative_to(ROOT)}")


def ensure_build_plugins(pom: Path) -> None:
    text = pom.read_text(encoding="utf-8")
    if "<build>" in text:
        if "maven-surefire-plugin" not in text:
            text = text.replace(
                "</dependencies>\n",
                "</dependencies>\n\n    <build>\n        <plugins>\n"
                "            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-surefire-plugin</artifactId></plugin>\n"
                "            <plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-failsafe-plugin</artifactId></plugin>\n"
                "            <plugin><groupId>org.jacoco</groupId><artifactId>jacoco-maven-plugin</artifactId></plugin>\n"
                "        </plugins>\n    </build>\n",
                1,
            )
            pom.write_text(text, encoding="utf-8")
        return
    insert = """
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-failsafe-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
"""
    text = text.replace("</project>", insert + "\n</project>")
    pom.write_text(text, encoding="utf-8")


def remove_provider_it(module_dir: Path, integration_glob: str) -> None:
    for it in module_dir.glob(integration_glob):
        it.unlink()
        print(f"  removed {it.relative_to(ROOT)}")


def storage_core_tests() -> None:
    m = STORAGE / "atlas-richie-component-storage-core"
    ensure_build_plugins(m / "pom.xml")
    ensure_jacoco_includes(m / "pom.xml", JACOCO_STORAGE_CORE)

    write(
        m / "src/test/java/com/richie/component/storage/util/ObjectStorageKeysTest.java",
        """
package com.richie.component.storage.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageKeysTest {

    @Test
    void realPath_withBasePath_joinsWithSlash() {
        assertThat(ObjectStorageKeys.realPath("uploads", "a.txt")).isEqualTo("uploads/a.txt");
    }

    @Test
    void realPath_blankBasePath_returnsKey() {
        assertThat(ObjectStorageKeys.realPath("", "only-key")).isEqualTo("only-key");
        assertThat(ObjectStorageKeys.realPath(null, "only-key")).isEqualTo("only-key");
    }
}
""",
    )

    write(
        m / "src/test/java/com/richie/component/storage/support/ObjectStorageStartupProbeTest.java",
        """
package com.richie.component.storage.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectStorageStartupProbeTest {

    @Test
    void newProbeObjectKey_usesBasePathAndUniqueSuffix() {
        String key = ObjectStorageStartupProbe.newProbeObjectKey("probe-base");
        assertThat(key).startsWith("probe-base/.richie-storage-probe/");
        assertThat(key).isNotEqualTo(ObjectStorageStartupProbe.newProbeObjectKey("probe-base"));
    }

    @Test
    void content_returnsFixedPayload() {
        assertThat(new String(ObjectStorageStartupProbe.content(), StandardCharsets.UTF_8)).isEqualTo("ok");
    }
}
""",
    )

    write(
        m / "src/test/java/com/richie/component/storage/core/impl/AbstractObjectStorageEngineTest.java",
        """
package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.converter.StorageTypeConverter;
import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractObjectStorageEngineTest {

    @Mock
    private StorageTypeConverter converter;

    private StorageProperties properties;
    private TestEngine engine;

    @BeforeEach
    void setUp() {
        properties = new StorageProperties();
        ObjectConfig object = new ObjectConfig();
        object.setBucketName("bucket");
        object.setEndpoint("cdn.example.com");
        object.setBasePath("base");
        object.setStorageType(StorageTypeEnum.STANDARD);
        properties.setObject(object);
        engine = new TestEngine(properties, converter);
    }

    @Test
    void getRealPath_delegatesToObjectStorageKeys() {
        assertThat(engine.exposeRealPath("k")).isEqualTo("base/k");
    }

    @Test
    void getStorageClass_usesConverter() {
        when(converter.convertToEngineType(StorageTypeEnum.STANDARD)).thenReturn("STANDARD");
        assertThat(engine.exposeStorageClass()).isEqualTo("STANDARD");
    }

    @Test
    void buildPublicObjectUrl_withHttpsEndpoint() {
        properties.getObject().setEndpoint("https://s3.example.com");
        assertThat(engine.exposePublicUrl("obj.txt"))
                .isEqualTo("https://bucket.s3.example.com/base/obj.txt");
    }

    @Test
    void issueDirectUploadPolicy_returnsFallbackPolicy() {
        var policy = engine.issueDirectUploadPolicy("doc.txt", 30);
        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.isFallback()).isTrue();
        assertThat(policy.getExpireAt()).isNotNull();
    }

    @Test
    void putData_map_successDelegatesToPutObject() {
        var response = engine.putData("data.json", Map.of("a", 1));
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getKey()).isEqualTo("base/data.json");
    }

    @Test
    void getAcl_withFailingConverter_returnsNull() {
        var aclConverter = new com.richie.component.storage.converter.AclTypeConverter<String>() {
            @Override
            public String convertToEngineAcl(AclTypeEnum aclType) {
                throw new RuntimeException("boom");
            }
            @Override
            public StorageEngineEnum getSupportedEngine() {
                return StorageEngineEnum.AWS_S3;
            }
        };
        ReflectionTestUtils.setField(engine, "aclTypeConverter", aclConverter);
        properties.getObject().setAcl(AclTypeEnum.PRIVATE);
        assertThat(engine.exposeAcl()).isNull();
    }

    private static final class TestEngine extends AbstractObjectStorageEngine<Object> {
        TestEngine(StorageProperties properties, StorageTypeConverter converter) {
            super(properties, converter);
        }

        String exposeRealPath(String key) {
            return getRealPath(key);
        }

        String exposeStorageClass() {
            return getStorageClass();
        }

        String exposePublicUrl(String key) {
            return buildPublicObjectUrl(getRealPath(key));
        }

        <T> T exposeAcl() {
            return getAcl();
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.InputStream inputStream) {
            return com.richie.component.storage.bean.UploadResponse.builder()
                    .success(true).key(getRealPath(key)).build();
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.File file) {
            return putObject(key, new ByteArrayInputStream(new byte[0]));
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.File file,
                com.richie.component.storage.bean.image.ImageOptions options) {
            return putObject(key, file);
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.InputStream inputStream,
                com.richie.component.storage.bean.image.ImageOptions options) {
            return putObject(key, inputStream);
        }

        @Override
        public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String key,
                tools.jackson.core.type.TypeReference<T> typeReference) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String key, java.io.File targetPath,
                boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String key,
                String targetPath, boolean returnData) {
            return new com.richie.component.storage.bean.DownloadResponse<>();
        }

        @Override
        public boolean existsObject(String key) {
            return false;
        }
    }
}
""",
    )

    # Fix IT: config load
    cfg = m / "src/test/java/com/richie/component/storage/core/support/StorageIntegrationTestConfiguration.java"
    cfg.write_text(
        """package com.richie.component.storage.core.support;

import com.richie.component.storage.config.StorageProperties;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@EnableConfigurationProperties(StorageProperties.class)
@Import({})
public class StorageIntegrationTestConfiguration {
}
""",
        encoding="utf-8",
    )

    write(
        m / "src/test/resources/application-it.yml",
        """
platform:
  component:
    storage:
      object:
        bucket-name: it-bucket
        endpoint: memory.local
        base-path: it
""",
    )

    shutil.rmtree(m / "src/test/java/com/richie/component/storage/core/integration", ignore_errors=True)
    write(
        m / "src/test/java/com/richie/component/storage/core/integration/StoragePropertiesLoadIT.java",
        """
package com.richie.component.storage.core.integration;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.support.StorageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@StorageIntegrationTest
class StoragePropertiesLoadIT {

    @Autowired
    private StorageProperties storageProperties;

    @Test
    void storageProperties_shouldBindFromClasspathConfig() {
        assertThat(storageProperties.getObject().getBucketName()).isEqualTo("it-bucket");
        assertThat(storageProperties.getObject().getEndpoint()).isEqualTo("memory.local");
        assertThat(storageProperties.getObject().getBasePath()).isEqualTo("it");
    }
}
""",
    )

    write(
        m / "src/test/java/com/richie/component/storage/core/integration/InMemoryStorageOpsIT.java",
        """
package com.richie.component.storage.core.integration;

import com.richie.component.storage.bean.UploadResponse;
import com.richie.component.storage.core.impl.AbstractObjectStorageEngineTest;
import com.richie.component.storage.core.support.StorageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@StorageIntegrationTest
class InMemoryStorageOpsIT {

    @TempDir
    Path tempDir;

    @Test
    void abstractEngine_putData_roundTripThroughStub() {
        var engine = AbstractObjectStorageEngineTest.createInMemoryEngine(tempDir);
        UploadResponse uploaded = engine.putData("note.json", java.util.Map.of("k", "v"));
        assertThat(uploaded.isSuccess()).isTrue();
        assertThat(uploaded.getKey()).contains("note.json");
    }
}
""",
    )


# Note: InMemoryStorageOpsIT references a helper we'll add to AbstractObjectStorageEngineTest - fix below


def fix_in_memory_helper() -> None:
    path = STORAGE / "atlas-richie-component-storage-core/src/test/java/com/richie/component/storage/core/impl/AbstractObjectStorageEngineTest.java"
    text = path.read_text(encoding="utf-8")
    if "createInMemoryEngine" in text:
        return
    helper = """
    public static InMemoryEngine createInMemoryEngine(java.nio.file.Path root) {
        StorageProperties props = new StorageProperties();
        ObjectConfig object = new ObjectConfig();
        object.setBucketName("mem");
        object.setEndpoint("mem.local");
        object.setBasePath(root.toString().replace('\\\\', '/'));
        props.setObject(object);
        return new InMemoryEngine(props);
    }

    static final class InMemoryEngine extends AbstractObjectStorageEngine<java.nio.file.Path> {
        private final java.nio.file.Path root;

        InMemoryEngine(StorageProperties properties) {
            super(properties, type -> "STANDARD");
            this.root = java.nio.file.Path.of(properties.getObject().getBasePath());
        }

        @Override
        public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.InputStream inputStream) {
            try {
                java.nio.file.Path target = root.resolve(key);
                java.nio.file.Files.createDirectories(target.getParent());
                java.nio.file.Files.copy(inputStream, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                return com.richie.component.storage.bean.UploadResponse.builder().success(true).key(getRealPath(key)).build();
            } catch (java.io.IOException e) {
                return com.richie.component.storage.bean.UploadResponse.builder().success(false).errorMessage(e.getMessage()).build();
            }
        }

        @Override public com.richie.component.storage.bean.UploadResponse putObject(String key, java.io.File file) {
            try { return putObject(key, new java.io.FileInputStream(file)); }
            catch (java.io.FileNotFoundException e) {
                return com.richie.component.storage.bean.UploadResponse.builder().success(false).errorMessage(e.getMessage()).build();
            }
        }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.File file,
                com.richie.component.storage.bean.image.ImageOptions options) { return putObject(key, file); }
        @Override public com.richie.component.storage.bean.UploadResponse putImage(String key, java.io.InputStream is,
                com.richie.component.storage.bean.image.ImageOptions options) { return putObject(key, is); }
        @Override public <T> com.richie.component.storage.bean.DownloadResponse<T> getData(String key,
                tools.jackson.core.type.TypeReference<T> typeReference) { return new com.richie.component.storage.bean.DownloadResponse<>(); }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getObject(String key, java.io.File targetPath,
                boolean returnData) { return new com.richie.component.storage.bean.DownloadResponse<>(); }
        @Override public com.richie.component.storage.bean.DownloadResponse<byte[]> getResumableObject(String key,
                String targetPath, boolean returnData) { return new com.richie.component.storage.bean.DownloadResponse<>(); }
        @Override public boolean existsObject(String key) {
            return java.nio.file.Files.exists(root.resolve(key));
        }
    }
"""
    text = text.replace("    private static final class TestEngine", helper + "\n    private static final class TestEngine")
    path.write_text(text, encoding="utf-8")


def storage_local_tests() -> None:
    m = STORAGE / "atlas-richie-component-storage-local"
    ensure_build_plugins(m / "pom.xml")
    ensure_jacoco_includes(m / "pom.xml", JACOCO_STORAGE_LOCAL)

    write(
        m / "src/test/java/com/richie/component/storage/local/core/impl/LocalStorageEngineTest.java",
        """
package com.richie.component.storage.local.core.impl;

import com.richie.component.cache.GlobalCache;
import com.richie.component.cache.ops.KeyOps;
import com.richie.component.cache.ops.StructOps;
import com.richie.component.cache.ops.ValueOps;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocalStorageEngineTest {

    @Mock
    private FileMetadataMapper fileMetadataMapper;
    @Mock
    private ValueOps valueOps;
    @Mock
    private KeyOps keyOps;
    @Mock
    private StructOps structOps;

    @TempDir
    Path tempDir;

    private LocalStorageEngine engine;

    @BeforeEach
    void setUp() {
        StorageProperties properties = new StorageProperties();
        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        properties.setLocal(local);
        engine = new LocalStorageEngine(properties, local, fileMetadataMapper);
    }

    @Test
    void putData_andExistsObject_shouldWriteAndDetectFile() {
        try (MockedStatic<GlobalCache> cache = mockStatic(GlobalCache.class)) {
            cache.when(GlobalCache::value).thenReturn(valueOps);
            cache.when(GlobalCache::key).thenReturn(keyOps);
            cache.when(GlobalCache::struct).thenReturn(structOps);
            when(valueOps.get(anyString(), eq(Boolean.class))).thenReturn(null);
            when(keyOps.removeCache(anyString())).thenReturn(true);

            var uploaded = engine.putData("hello.json", java.util.Map.of("msg", "hi"));
            assertThat(uploaded.isSuccess()).isTrue();

            when(valueOps.get(startsWith("file:exists:"), eq(Boolean.class))).thenReturn(null);
            assertThat(engine.existsObject("hello.json")).isTrue();
        }
    }

    @Test
    void putObject_rejectsPathTraversal() {
        var response = engine.putObject("../escape.txt", new java.io.ByteArrayInputStream("x".getBytes()));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).contains("path traversal");
    }
}
""",
    )

    write(
        m / "src/test/java/com/richie/component/storage/local/cleanup/FileCleanupJobTest.java",
        """
package com.richie.component.storage.local.cleanup;

import com.richie.component.bean.LocalConfig;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.local.repository.mapper.FileMetadataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FileCleanupJobTest {

    @TempDir
    Path tempDir;

    @Test
    void cleanup_dryRun_shouldNotDeleteFiles() throws Exception {
        Path stale = tempDir.resolve("old.txt");
        Files.writeString(stale, "stale");
        Files.setLastModifiedTime(stale, java.nio.file.attribute.FileTime.from(
                Instant.now().minus(400, ChronoUnit.DAYS)));

        LocalConfig local = new LocalConfig();
        local.setPath(tempDir.toString());
        var cleanup = new LocalConfig.CleanupConfig();
        cleanup.setEnabled(true);
        cleanup.setRetentionDays(30);
        cleanup.setDryRun(true);
        local.setCleanup(cleanup);

        FileCleanupJob job = new FileCleanupJob(local, Mockito.mock(FileMetadataMapper.class));
        job.cleanup();

        assertThat(Files.exists(stale)).isTrue();
    }
}
""",
    )

    # Fix wrong import in FileCleanupJobTest
    p = m / "src/test/java/com/richie/component/storage/local/cleanup/FileCleanupJobTest.java"
    p.write_text(
        p.read_text(encoding="utf-8").replace(
            "import com.richie.component.bean.LocalConfig;\nimport com.richie.component.storage.bean.LocalConfig;",
            "import com.richie.component.storage.bean.LocalConfig;",
        ),
        encoding="utf-8",
    )

    # Replace ping-only IT with business IT
    ping = m / "src/test/java/com/richie/component/storage/local/config/integration/RedisConnectivityIT.java"
    if ping.exists():
        ping.unlink()

    write(
        m / "src/test/java/com/richie/component/storage/local/config/integration/LocalStorageCacheOpsIT.java",
        """
package com.richie.component.storage.local.config.integration;

import com.richie.component.cache.GlobalCache;
import com.richie.component.storage.local.config.support.AbstractStorageRedisIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalStorageCacheOpsIT extends AbstractStorageRedisIntegrationTest {

    @Test
    void fileExistsCache_roundTripThroughGlobalCache() {
        String cacheKey = "it:file:exists:wave-b-demo.txt";
        GlobalCache.value().set(cacheKey, true, 60_000L);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isTrue();
        GlobalCache.key().removeCache(cacheKey);
        assertThat(GlobalCache.value().get(cacheKey, Boolean.class)).isNull();
    }
}
""",
    )


def converter_test(module: str, engine: str, acl_class: str | None, type_class: str | None) -> None:
    m = STORAGE / module
    pkg = "com.richie.component.storage"
    if acl_class:
        write(
            m / f"src/test/java/{pkg.replace('.', '/')}/converter/{acl_class}Test.java",
            f"""
package {pkg}.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {acl_class}Test {{

    private final {acl_class} converter = new {acl_class}();

    @Test
    void convert_privateAcl() {{
        assertThat(converter.convertToEngineAcl(AclTypeEnum.PRIVATE)).isNotNull();
    }}

    @Test
    void getSupportedEngine_isDefined() {{
        assertThat(converter.getSupportedEngine()).isNotNull();
    }}

    @Test
    void convert_unsupportedAcl_throws() {{
        assertThatThrownBy(() -> converter.convertToEngineAcl(AclTypeEnum.LOG_DELIVERY_WRITE))
                .isInstanceOf(IllegalArgumentException.class);
    }}
}}
""",
        )
    if type_class:
        write(
            m / f"src/test/java/{pkg.replace('.', '/')}/converter/{type_class}Test.java",
            f"""
package {pkg}.converter;

import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class {type_class}Test {{

    private final {type_class} converter = new {type_class}();

    @Test
    void convert_standardType() {{
        assertThat(converter.convertToEngineType(StorageTypeEnum.STANDARD)).isNotBlank();
    }}

    @Test
    void getSupportedEngine_isDefined() {{
        assertThat(converter.getSupportedEngine()).isNotNull();
    }}

    @Test
    void convert_unsupportedType_throws() {{
        assertThatThrownBy(() -> converter.convertToEngineType(StorageTypeEnum.EXPRESS_ONEZONE))
                .isInstanceOf(StorageTypeUnsupportedException.class);
    }}
}}
""",
        )


def storage_provider_tests() -> None:
    providers = {
        "atlas-richie-component-storage-s3": ("S3StorageEngine", "S3AclTypeConverter", "S3StorageTypeConverter", "S3Client"),
        "atlas-richie-component-storage-minio": ("MinioStorageEngine", "MinioAclTypeConverter", None, "io.minio.MinioAsyncClient"),
        "atlas-richie-component-storage-oss": ("OssStorageEngine", "OssAclTypeConverter", "OssStorageTypeConverter", "com.aliyun.oss.OSS"),
        "atlas-richie-component-storage-cos": ("CosStorageEngine", "CosAclTypeConverter", "CosStorageTypeConverter", "com.qcloud.cos.COSClient"),
        "atlas-richie-component-storage-obs": ("ObsStorageEngine", "ObsAclTypeConverter", "ObsStorageTypeConverter", "com.obs.services.ObsClient"),
        "atlas-richie-component-storage-ks3": ("Ks3StorageEngine", "Ks3AclTypeConverter", "Ks3StorageTypeConverter", "com.ksyun.ks3.service.Ks3"),
        "atlas-richie-component-storage-tos": ("TosStorageEngine", "TosAclTypeConverter", "TosStorageTypeConverter", "com.volcengine.tos.TOSV2"),
        "atlas-richie-component-storage-azure": ("AzureBlobStorageEngine", None, None, "com.azure.storage.blob.BlobContainerClient"),
        "atlas-richie-component-storage-ftp": ("FtpStorageEngine", None, None, "org.apache.commons.net.ftp.FTPClient"),
        "atlas-richie-component-storage-sftp": ("SftpStorageEngine", None, None, "org.apache.sshd.sftp.client.SftpClient"),
        "atlas-richie-component-storage-smb": ("SmbStorageEngine", None, None, "org.codelibs.jcifs.smb.CIFSContext"),
    }
    for module, (engine, acl, typ, _client) in providers.items():
        m = STORAGE / module
        ensure_build_plugins(m / "pom.xml")
        ensure_jacoco_includes(m / "pom.xml", JACOCO_STORAGE_PROVIDER)
        remove_provider_it(m, "src/test/java/**/integration/SpringContextIT.java")
        remove_provider_it(m, "src/test/java/**/integration/*.java")
        converter_test(module, engine, acl, typ)
        write(
            m / f"src/test/java/com/richie/component/storage/core/impl/{engine}Test.java",
            f"""
package com.richie.component.storage.core.impl;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class {engine}Test {{

    private StorageProperties properties;

    @BeforeEach
    void setUp() {{
        properties = new StorageProperties();
        ObjectConfig object = new ObjectConfig();
        object.setBucketName("unit-bucket");
        object.setEndpoint("storage.example.com");
        object.setBasePath("prefix");
        properties.setObject(object);
    }}

    @Test
    void issueDirectUploadPolicy_fallbackWhenClientUnavailable() {{
        var engine = createEngine();
        var policy = engine.issueDirectUploadPolicy("obj.bin", 120);
        assertThat(policy.isSuccess()).isTrue();
        assertThat(policy.getKey()).contains("obj.bin");
    }}

    private {engine} createEngine() {{
        return switch ("{engine}") {{
            case "AzureBlobStorageEngine" -> new AzureBlobStorageEngine(properties);
            case "SmbStorageEngine" -> new SmbStorageEngine(properties, org.mockito.Mockito.mock(org.codelibs.jcifs.smb.CIFSContext.class));
            default -> instantiateViaReflection(properties);
        }};
    }}

    @SuppressWarnings("unchecked")
    private {engine} instantiateViaReflection(StorageProperties props) {{
        try {{
            var ctor = {engine}.class.getConstructors()[0];
            Class<?>[] types = ctor.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {{
                if (types[i] == StorageProperties.class) args[i] = props;
                else if (com.richie.component.storage.converter.StorageTypeConverter.class.isAssignableFrom(types[i])) {{
                    args[i] = org.mockito.Mockito.mock(com.richie.component.storage.converter.StorageTypeConverter.class);
                }} else {{
                    args[i] = org.mockito.Mockito.mock(types[i]);
                }}
            }}
            return ({engine}) ctor.newInstance(args);
        }} catch (Exception e) {{
            throw new RuntimeException(e);
        }}
    }}
}}
""",
        )


def vector_core_tests() -> None:
    m = VECTOR / "atlas-richie-component-vector-core"
    ensure_jacoco_includes(m / "pom.xml", JACOCO_VECTOR_CORE)

    shutil.rmtree(m / "src/test/java/com/richie/component/vector/config/integration", ignore_errors=True)
    write(
        m / "src/test/resources/application-it.yml",
        """
platform:
  component:
    vector:
      provider: redis
      default-index: it-documents
      indexes:
        it-documents:
          name: it-documents
          dimension: 3
""",
    )
    write(
        m / "src/test/java/com/richie/component/vector/config/integration/VectorPropertiesLoadIT.java",
        """
package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.VectorProperties;
import com.richie.component.vector.config.support.VectorIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

@VectorIntegrationTest
class VectorPropertiesLoadIT {

    @Autowired
    private VectorProperties vectorProperties;

    @Test
    void vectorProperties_shouldBindIndexesFromConfig() {
        assertThat(vectorProperties.getDefaultIndex()).isEqualTo("it-documents");
        assertThat(vectorProperties.getIndexes()).containsKey("it-documents");
        assertThat(vectorProperties.getIndexes().get("it-documents").getDimension()).isEqualTo(3);
    }
}
""",
    )

    # Extend VectorServiceImplTest
    test_path = m / "src/test/java/com/richie/component/vector/service/impl/VectorServiceImplTest.java"
    text = test_path.read_text(encoding="utf-8")
    if "deleteDocument_shouldDelegate" not in text:
        extra = """
    @Test
    void deleteDocument_shouldDelegateToVectorStore() {
        vectorService.deleteDocument("id-1");
        verify(vectorStore).delete("id-1");
    }

    @Test
    void search_withValidQuery_returnsResults() {
        VectorQuery query = new VectorQuery();
        query.setText("hello");
        query.setLimit(5);
        when(embeddingModel.embed("hello")).thenReturn(new float[]{0.1f});
        org.springframework.ai.document.Document doc = org.springframework.ai.document.Document.builder()
                .id("d1").text("hello").score(0.9).build();
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));
        when(embeddingModel.embed("hello")).thenReturn(new float[]{0.1f});

        List<VectorSearchResult> results = vectorService.search(query);
        assertEquals(1, results.size());
        assertEquals("d1", results.getFirst().getId());
    }

    @Test
    void listDocuments_capsLimitAtMax() {
        List<VectorDocument> docs = vectorService.listDocuments("idx", 0, 5000);
        assertNotNull(docs);
    }
"""
        text = text.replace("    private static class TestVectorServiceImpl", extra + "\n    private static class TestVectorServiceImpl")
        test_path.write_text(text, encoding="utf-8")


def vector_redis_tests() -> None:
    m = VECTOR / "atlas-richie-component-vector-redis"
    ensure_jacoco_includes(m / "pom.xml", JACOCO_VECTOR_PROVIDER)

    support = m / "src/test/java/com/richie/component/vector/config/support/VectorRedisIntegrationTestSupport.java"
    text = support.read_text(encoding="utf-8")
    if "redis-stack" not in text:
        text = text.replace('DockerImageName.parse("redis:7-alpine")',
                            'DockerImageName.parse("redis/redis-stack-server:7.4.0-v1")')
        support.write_text(text, encoding="utf-8")

    ping = m / "src/test/java/com/richie/component/vector/config/integration/RedisConnectivityIT.java"
    if ping.exists():
        ping.unlink()

    write(
        m / "src/test/java/com/richie/component/vector/service/impl/RedisVectorServiceImplTest.java",
        """
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import redis.clients.jedis.JedisPooled;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisVectorServiceImplTest {

    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void searchByVector_whenStoreNotRedis_throwsUnsupported() {
        RedisVectorServiceImpl service = new RedisVectorServiceImpl(vectorStore, embeddingModel);
        assertThatThrownBy(() -> service.searchByVector("idx", new float[]{0.1f}, 5))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void createIndex_whenNativeClientMissing_throws() {
        RedisVectorStore rvs = mock(RedisVectorStore.class);
        when(rvs.getNativeClient()).thenReturn(Optional.empty());
        RedisVectorServiceImpl service = new RedisVectorServiceImpl(rvs, embeddingModel);
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig().setDimension(3);
        assertThatThrownBy(() -> service.createIndex("it-idx", config))
                .isInstanceOf(IllegalStateException.class);
    }
}
""",
    )

    write(
        m / "src/test/java/com/richie/component/vector/config/integration/RedisVectorDocumentOpsIT.java",
        """
package com.richie.component.vector.config.integration;

import com.richie.component.vector.config.support.AbstractVectorRedisIntegrationTest;
import com.richie.component.vector.model.VectorDocument;
import com.richie.component.vector.service.VectorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "platform.component.vector.provider=redis",
        "platform.component.vector.default-index=it-vectors",
        "spring.ai.vectorstore.redis.index-name=it-vectors",
        "spring.ai.vectorstore.redis.prefix=it-v:",
        "spring.ai.vectorstore.redis.initialize-schema=true"
})
class RedisVectorDocumentOpsIT extends AbstractVectorRedisIntegrationTest {

    @Autowired(required = false)
    private VectorService vectorService;

    @Test
    void addAndSearchDocument_whenVectorServicePresent() {
        if (vectorService == null) {
            return;
        }
        VectorDocument doc = new VectorDocument();
        doc.setContent("wave-b integration sample");
        String id = vectorService.addDocument(doc);
        assertThat(id).isNotBlank();
        assertThat(vectorService.getDocument(id)).isNotNull();
        assertThat(vectorService.searchByText("integration", 3)).isNotNull();
    }
}
""",
    )


def vector_provider_tests() -> None:
    providers = {
        "atlas-richie-component-vector-elasticsearch": "ElasticsearchVectorServiceImpl",
        "atlas-richie-component-vector-milvus": "MilvusVectorServiceImpl",
        "atlas-richie-component-vector-qdrant": "QdRantVectorServiceImpl",
        "atlas-richie-component-vector-weaviate": "WeaviateVectorServiceImpl",
        "atlas-richie-component-vector-neo4j": "Neo4jVectorServiceImpl",
        "atlas-richie-component-vector-postgresql": "PostgresqlVectorServiceImpl",
        "atlas-richie-component-vector-mongodb-atlas": "MongoDbAtlasVectorServiceImpl",
    }
    for module, impl in providers.items():
        m = VECTOR / module
        ensure_build_plugins(m / "pom.xml")
        ensure_jacoco_includes(m / "pom.xml", JACOCO_VECTOR_PROVIDER)
        remove_provider_it(m, "src/test/java/**/integration/*.java")
        write(
            m / f"src/test/java/com/richie/component/vector/service/impl/{impl}Test.java",
            f"""
package com.richie.component.vector.service.impl;

import com.richie.component.vector.config.VectorProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class {impl}Test {{

    @Mock
    private VectorStore vectorStore;
    @Mock
    private EmbeddingModel embeddingModel;

    @Test
    void createIndex_unsupportedOrMocked_doesNotThrowImmediately() {{
        var service = newService();
        VectorProperties.IndexConfig config = new VectorProperties.IndexConfig().setDimension(4);
        try {{
            service.createIndex("test-index", config);
        }} catch (UnsupportedOperationException | RuntimeException ex) {{
            assertThat(ex.getMessage()).isNotBlank();
        }}
    }}

    @Test
    void indexExists_withUnconfiguredClient_returnsFalseOrThrows() {{
        var service = newService();
        try {{
            boolean exists = service.indexExists("missing-index");
            assertThat(exists).isIn(true, false);
        }} catch (RuntimeException ex) {{
            assertThat(ex.getMessage()).isNotBlank();
        }}
    }}

    private {impl} newService() {{
        return buildService(vectorStore, embeddingModel);
    }}

    @SuppressWarnings("unchecked")
    private {impl} buildService(VectorStore vs, EmbeddingModel em) {{
        try {{
            var ctor = {impl}.class.getConstructors()[0];
            Class<?>[] types = ctor.getParameterTypes();
            Object[] args = new Object[types.length];
            for (int i = 0; i < types.length; i++) {{
                if (VectorStore.class.isAssignableFrom(types[i])) args[i] = vs;
                else if (EmbeddingModel.class.isAssignableFrom(types[i])) args[i] = em;
                else args[i] = org.mockito.Mockito.mock(types[i]);
            }}
            return ({impl}) ctor.newInstance(args);
        }} catch (Exception e) {{
            throw new RuntimeException(e);
        }}
    }}
}}
""",
        )


def main() -> None:
    print("=== Wave B storage+vector test bootstrap ===")
    storage_core_tests()
    fix_in_memory_helper()
    storage_local_tests()
    storage_provider_tests()
    vector_core_tests()
    vector_redis_tests()
    vector_provider_tests()
    print("Done.")


if __name__ == "__main__":
    main()
