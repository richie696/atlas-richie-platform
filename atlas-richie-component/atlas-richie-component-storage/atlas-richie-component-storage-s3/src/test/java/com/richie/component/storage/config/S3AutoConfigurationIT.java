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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Testcontainers
@EnabledIf("isDockerAvailable")
@SpringBootTest(
    classes = S3AutoConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "platform.component.storage.object.engine=aws_s3",
    "platform.component.storage.object.endpoint=http://127.0.0.1:${minio.port}",
    "platform.component.storage.object.access-key-id=minioadmin",
    "platform.component.storage.object.access-key-secret=minioadmin",
    "platform.component.storage.object.bucket-name=it-test-bucket",
    "platform.component.storage.object.region=us-east-1",
    "platform.component.storage.object.base-path=test",
    "platform.component.storage.object.auto-create-bucket=true"
})
class S3AutoConfigurationIT {

    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>("minio/minio:latest")
            .withCommand("server /data --console-address :9001")
            .withExposedPorts(9000);

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
            () -> "http://127.0.0.1:" + minioContainer.getMappedPort(9000));
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void s3ClientBean_shouldBeCreated() {
        S3Client client = applicationContext.getBean(S3Client.class);
        assertThat(client).isNotNull();
    }

    @Test
    void s3PresignerBean_shouldBeCreated() {
        S3Presigner presigner = applicationContext.getBean(S3Presigner.class);
        assertThat(presigner).isNotNull();
    }

    @Test
    void bucket_shouldBeAutoCreated() throws Exception {
        S3Client client = applicationContext.getBean(S3Client.class);
        HeadBucketResponse response = client.headBucket(HeadBucketRequest.builder()
                .bucket("it-test-bucket")
                .build());
        assertThat(response).isNotNull();
    }
}
