package com.richie.component.storage.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = S3AutoConfigurationNotActivatedTest.TestConfiguration.class,
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
class S3AutoConfigurationNotActivatedTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void s3ClientBean_shouldNotExistWhenEngineIsNotAwsS3() {
        assertThat(applicationContext.getBeansOfType(S3Client.class)).isEmpty();
    }

    @Test
    void s3PresignerBean_shouldNotExistWhenEngineIsNotAwsS3() {
        assertThat(applicationContext.getBeansOfType(S3Presigner.class)).isEmpty();
    }

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan("com.richie.component.storage")
    static class TestConfiguration {
    }
}

@SpringBootTest(
        classes = S3AutoConfiguration.class,
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
class S3AutoConfigurationAutoDiscoveryTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void context_shouldLoadWithS3AutoConfigurationAsMainClass() {
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeansOfType(StorageProperties.class)).isNotEmpty();
        assertThat(applicationContext.getBeansOfType(S3Client.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(S3Presigner.class)).isEmpty();
    }
}
