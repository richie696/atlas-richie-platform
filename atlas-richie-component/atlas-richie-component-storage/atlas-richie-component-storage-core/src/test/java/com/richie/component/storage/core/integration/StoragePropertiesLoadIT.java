package com.richie.component.storage.core.integration;

import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.support.StorageIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@StorageIntegrationTest
@TestPropertySource(properties = {
        "platform.component.storage.object.bucket-name=it-bucket",
        "platform.component.storage.object.endpoint=memory.local",
        "platform.component.storage.object.base-path=it"
})
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
