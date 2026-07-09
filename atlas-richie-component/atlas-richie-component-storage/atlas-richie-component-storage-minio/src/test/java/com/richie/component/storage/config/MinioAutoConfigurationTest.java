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
package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.enums.AclTypeEnum;
import com.richie.component.storage.enums.StorageTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = MinioAutoConfigurationTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "platform.component.storage.object.engine=minio",
        "platform.component.storage.object.endpoint=http://127.0.0.1:9000",
        "platform.component.storage.object.access-key-id=test-key",
        "platform.component.storage.object.access-key-secret=test-secret",
        "platform.component.storage.object.bucket-name=test-bucket",
        "platform.component.storage.object.region=us-east-1"
})
class MinioAutoConfigurationTest {

    @Test
    void minioAutoConfiguration_shouldHaveConfigurationAnnotation() {
        MinioAutoConfiguration config = new MinioAutoConfiguration();
        Configuration configuration = config.getClass().getAnnotation(Configuration.class);
        assertThat(configuration).isNotNull();
    }

    @Test
    void minioAutoConfiguration_shouldNotHaveClassLevelConditionalOnPropertyAnnotation() {
        MinioAutoConfiguration config = new MinioAutoConfiguration();
        ConditionalOnProperty conditionalOnProperty = config.getClass().getAnnotation(ConditionalOnProperty.class);
        assertThat(conditionalOnProperty).isNull();
    }

    @Test
    void minioAutoConfiguration_shouldHaveComponentScanAnnotation() {
        MinioAutoConfiguration config = new MinioAutoConfiguration();
        ComponentScan componentScan = config.getClass().getAnnotation(ComponentScan.class);
        assertThat(componentScan).isNotNull();
        assertThat(componentScan.value()).contains("com.richie.component.storage");
    }

    @Test
    void minioAutoConfiguration_shouldHaveEnableConfigurationPropertiesAnnotation() {
        MinioAutoConfiguration config = new MinioAutoConfiguration();
        EnableConfigurationProperties enableConfigurationProperties = config.getClass()
                .getAnnotation(EnableConfigurationProperties.class);
        assertThat(enableConfigurationProperties).isNotNull();
        assertThat(enableConfigurationProperties.value()).contains(StorageProperties.class);
    }

    @Test
    void minioAsyncClientMethod_shouldHaveBeanAnnotation() throws NoSuchMethodException {
        Bean bean = MinioAutoConfiguration.class.getMethod("minioAsyncClient", StorageProperties.class)
                .getAnnotation(Bean.class);
        assertThat(bean).isNotNull();
    }

    @Test
    void minioAsyncClientMethod_shouldHaveScopeAnnotation() throws NoSuchMethodException {
        Scope scope = MinioAutoConfiguration.class.getMethod("minioAsyncClient", StorageProperties.class)
                .getAnnotation(Scope.class);
        assertThat(scope).isNotNull();
        assertThat(scope.value()).isEqualTo("prototype");
    }

    @Test
    void minioAsyncClientMethod_shouldHaveConditionalOnPropertyAnnotation() throws NoSuchMethodException {
        ConditionalOnProperty[] annotations = MinioAutoConfiguration.class
                .getMethod("minioAsyncClient", StorageProperties.class)
                .getAnnotationsByType(ConditionalOnProperty.class);
        assertThat(annotations).hasSize(2);
        assertThat(annotations).anyMatch(a ->
                a.prefix().equals("platform.component.storage.object")
                        && a.name().length == 1
                        && "engine".equals(a.name()[0])
                        && "minio".equals(a.havingValue()));
        assertThat(annotations).anyMatch(a ->
                a.prefix().equals("platform.component.storage")
                        && a.name().length == 1
                        && "auto-init".equals(a.name()[0])
                        && "true".equals(a.havingValue())
                        && a.matchIfMissing());
    }

    @Test
    void storageProperties_shouldBeCreatedAndBound(@Autowired StorageProperties storageProperties) {
        assertThat(storageProperties).isNotNull();
        assertThat(storageProperties.getObject()).isNotNull();
    }

    @Test
    void objectConfig_shouldHaveCorrectValues(@Autowired StorageProperties storageProperties) {
        ObjectConfig objectConfig = storageProperties.getObject();
        assertThat(objectConfig.getEndpoint()).isEqualTo("http://127.0.0.1:9000");
        assertThat(objectConfig.getAccessKeyId()).isEqualTo("test-key");
        assertThat(objectConfig.getAccessKeySecret()).isEqualTo("test-secret");
        assertThat(objectConfig.getBucketName()).isEqualTo("test-bucket");
        assertThat(objectConfig.getRegion()).isEqualTo("us-east-1");
        assertThat(objectConfig.getEngine().name()).isEqualTo("MINIO");
    }

    @Test
    void objectConfig_shouldHaveCorrectDefaultValues() {
        ObjectConfig objectConfig = new ObjectConfig();
        assertThat(objectConfig.isAutoCreateBucket()).isTrue();
        assertThat(objectConfig.getAcl()).isEqualTo(AclTypeEnum.PUBLIC_READ);
        assertThat(objectConfig.getStorageType()).isEqualTo(StorageTypeEnum.STANDARD);
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(StorageProperties.class)
    @Import(MinioAutoConfiguration.class)
    static class TestConfiguration {
    }
}

@SpringBootTest(
        classes = MinioAutoConfigurationNotActivatedTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "platform.component.storage.object.engine=aws_s3",
        "platform.component.storage.object.endpoint=http://127.0.0.1:9000",
        "platform.component.storage.object.access-key-id=test-key",
        "platform.component.storage.object.access-key-secret=test-secret",
        "platform.component.storage.object.bucket-name=test-bucket",
        "platform.component.storage.object.region=us-east-1"
})
class MinioAutoConfigurationNotActivatedTest {

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void minioAsyncClientBean_shouldNotExistWhenEngineIsNotMinio() {
        assertThat(applicationContext.getBeansOfType(io.minio.MinioAsyncClient.class)).isEmpty();
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(StorageProperties.class)
    @Import(MinioAutoConfiguration.class)
    static class TestConfiguration {
    }
}

@SpringBootTest(
        classes = MinioAutoConfigurationAutoDiscoveryTest.TestConfiguration.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
        "platform.component.storage.object.engine=minio",
        "platform.component.storage.object.endpoint=http://127.0.0.1:9000",
        "platform.component.storage.object.access-key-id=test-key",
        "platform.component.storage.object.access-key-secret=test-secret",
        "platform.component.storage.object.bucket-name=test-bucket",
        "platform.component.storage.object.region=us-east-1"
})
class MinioAutoConfigurationAutoDiscoveryTest {

    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    @Test
    void context_shouldLoadWithMinioAutoConfigurationAsMainClass() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeansOfType(StorageProperties.class)).isNotEmpty();
    }

    @Configuration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(StorageProperties.class)
    @Import(MinioAutoConfiguration.class)
    static class TestConfiguration {
    }
}
