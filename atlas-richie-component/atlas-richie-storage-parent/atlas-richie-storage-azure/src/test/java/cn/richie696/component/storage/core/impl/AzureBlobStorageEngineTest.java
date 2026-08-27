/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package cn.richie696.component.storage.core.impl;

import cn.richie696.component.storage.bean.ObjectConfig;
import cn.richie696.component.storage.config.StorageProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AzureBlobStorageEngineTest {

    @Test
    void publicObjectUrl_usesAzureContainerPathAndBasePath() {
        StorageProperties properties = new StorageProperties();
        ObjectConfig objectConfig = properties.getObject();
        objectConfig.setEndpoint("account.blob.core.windows.net");
        objectConfig.setBucketName("container");
        objectConfig.setBasePath("base");

        AzureBlobStorageEngine engine = new AzureBlobStorageEngine(properties);

        assertThat(engine.publicObjectUrl("nested/file.pdf"))
                .isEqualTo("https://account.blob.core.windows.net/container/base/nested/file.pdf");
        assertThat(engine.publicObjectUrl("base/nested/file.pdf"))
                .isEqualTo("https://account.blob.core.windows.net/container/base/nested/file.pdf");
    }

    @Test
    void publicObjectUrl_doesNotDuplicateContainerWhenEndpointAlreadyIncludesIt() {
        StorageProperties properties = new StorageProperties();
        ObjectConfig objectConfig = properties.getObject();
        objectConfig.setEndpoint("https://account.blob.core.windows.net/container");
        objectConfig.setBucketName("container");

        AzureBlobStorageEngine engine = new AzureBlobStorageEngine(properties);

        assertThat(engine.publicObjectUrl("file.txt"))
                .isEqualTo("https://account.blob.core.windows.net/container/file.txt");
    }
}
