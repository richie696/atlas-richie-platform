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

import com.richie.component.storage.bean.FtpConfig;
import com.richie.component.storage.bean.LocalConfig;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.bean.SftpConfig;
import com.richie.component.storage.bean.Smb3Config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储配置属性单元测试
 * <p>
 * 覆盖默认值、字段结构、注解等。
 */
class StoragePropertiesTest {

    @Test
    void shouldHaveConfigurationPropertiesAnnotationWithExpectedPrefix() {
        ConfigurationProperties annotation = StorageProperties.class
                .getAnnotation(ConfigurationProperties.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.prefix()).isEqualTo("platform.component.storage");
    }

    @Test
    void autoInit_defaultValueShouldBeTrue() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getAutoInit()).isTrue();
    }

    @Test
    void autoInit_canBeSetToFalse() {
        StorageProperties properties = new StorageProperties();
        properties.setAutoInit(false);
        assertThat(properties.getAutoInit()).isFalse();
    }

    @Test
    void autoInit_canBeSetToNull() {
        StorageProperties properties = new StorageProperties();
        properties.setAutoInit(null);
        assertThat(properties.getAutoInit()).isNull();
    }

    @Test
    void local_defaultValueShouldBeInitialized() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getLocal()).isNotNull();
        assertThat(properties.getLocal().getPath()).isEqualTo("./storage/");
    }

    @Test
    void ftp_defaultValueShouldBeInitialized() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getFtp()).isNotNull();
    }

    @Test
    void sftp_defaultValueShouldBeInitialized() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getSftp()).isNotNull();
    }

    @Test
    void smb3_defaultValueShouldBeInitialized() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getSmb3()).isNotNull();
    }

    @Test
    void object_defaultValueShouldBeInitialized() {
        StorageProperties properties = new StorageProperties();
        assertThat(properties.getObject()).isNotNull();
    }

    @Test
    void subConfigSetters_shouldWork() throws Exception {
        StorageProperties properties = new StorageProperties();
        LocalConfig local = new LocalConfig("/tmp/storage/");
        properties.setLocal(local);
        assertThat(properties.getLocal()).isSameAs(local);

        FtpConfig ftp = new FtpConfig();
        properties.setFtp(ftp);
        assertThat(properties.getFtp()).isSameAs(ftp);

        SftpConfig sftp = new SftpConfig();
        properties.setSftp(sftp);
        assertThat(properties.getSftp()).isSameAs(sftp);

        Smb3Config smb3 = new Smb3Config();
        properties.setSmb3(smb3);
        assertThat(properties.getSmb3()).isSameAs(smb3);

        ObjectConfig object = new ObjectConfig();
        properties.setObject(object);
        assertThat(properties.getObject()).isSameAs(object);
    }

    @Test
    void allFields_shouldHavePublicSetter() throws NoSuchMethodException {
        // autoInit
        Method setAutoInit = StorageProperties.class.getMethod("setAutoInit", Boolean.class);
        assertThat(setAutoInit).isNotNull();

        Method setLocal = StorageProperties.class.getMethod("setLocal", LocalConfig.class);
        assertThat(setLocal).isNotNull();

        Method setFtp = StorageProperties.class.getMethod("setFtp", FtpConfig.class);
        assertThat(setFtp).isNotNull();

        Method setSftp = StorageProperties.class.getMethod("setSftp", SftpConfig.class);
        assertThat(setSftp).isNotNull();

        Method setSmb3 = StorageProperties.class.getMethod("setSmb3", Smb3Config.class);
        assertThat(setSmb3).isNotNull();

        Method setObject = StorageProperties.class.getMethod("setObject", ObjectConfig.class);
        assertThat(setObject).isNotNull();
    }

    @Test
    void builder_shouldProduceInstanceWithDefaults() {
        StorageProperties properties = StorageProperties.builder().build();
        assertThat(properties.getAutoInit()).isTrue();
        assertThat(properties.getLocal()).isNotNull();
        assertThat(properties.getLocal().getPath()).isEqualTo("./storage/");
        assertThat(properties.getFtp()).isNotNull();
        assertThat(properties.getSftp()).isNotNull();
        assertThat(properties.getSmb3()).isNotNull();
        assertThat(properties.getObject()).isNotNull();
    }

    @Test
    void builder_shouldAllowOverridingFields() {
        LocalConfig customLocal = new LocalConfig("/custom/path");
        StorageProperties properties = StorageProperties.builder()
                .autoInit(false)
                .local(customLocal)
                .build();
        assertThat(properties.getAutoInit()).isFalse();
        assertThat(properties.getLocal()).isSameAs(customLocal);
    }

    @Test
    void builder_shouldSupportPartialOverride() {
        StorageProperties properties = StorageProperties.builder()
                .object(new ObjectConfig())
                .build();
        assertThat(properties.getObject()).isNotNull();
        assertThat(properties.getAutoInit()).isTrue();
        assertThat(properties.getLocal()).isNotNull();
    }
}
