/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 存储引擎枚举单元测试
 * <p>
 * 覆盖 fromConfigValue / validConfigValues / isObjectStorage / 配置值映射。
 */
class StorageEngineEnumTest {

    @Test
    void values_shouldContainAll12Engines() {
        assertThat(StorageEngineEnum.values()).hasSize(12);
    }

    @Test
    void objectStorageEngines_shouldBeExactly8() {
        long count = 0;
        for (StorageEngineEnum e : StorageEngineEnum.values()) {
            if (e.isObjectStorage()) {
                count++;
            }
        }
        assertThat(count).isEqualTo(8);
    }

    @Test
    void fileProtocolEngines_shouldBeExactly4() {
        long count = 0;
        for (StorageEngineEnum e : StorageEngineEnum.values()) {
            if (!e.isObjectStorage()) {
                count++;
            }
        }
        assertThat(count).isEqualTo(4);
    }

    @ParameterizedTest
    @ValueSource(strings = {"minio", "aliyun_oss", "tencent_cos", "huawei_obs",
            "aws_s3", "ksyun_ks3", "volcengine_tos", "azure_blob"})
    void fromConfigValue_shouldResolveAllObjectStorageEngines(String configValue) {
        Optional<StorageEngineEnum> engine = StorageEngineEnum.fromConfigValue(configValue);
        assertThat(engine).isPresent();
        assertThat(engine.get().isObjectStorage()).isTrue();
        assertThat(engine.get().getConfigValue()).isEqualTo(configValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp", "sftp", "smb", "local"})
    void fromConfigValue_shouldResolveAllFileProtocolEngines(String configValue) {
        Optional<StorageEngineEnum> engine = StorageEngineEnum.fromConfigValue(configValue);
        assertThat(engine).isPresent();
        assertThat(engine.get().isObjectStorage()).isFalse();
        assertThat(engine.get().getConfigValue()).isEqualTo(configValue);
    }

    @Test
    void fromConfigValue_shouldReturnEmptyForUnknownValue() {
        assertThat(StorageEngineEnum.fromConfigValue("unknown_engine")).isEmpty();
        assertThat(StorageEngineEnum.fromConfigValue("")).isEmpty();
        assertThat(StorageEngineEnum.fromConfigValue("MINIO")).isEmpty();
    }

    @Test
    void fromConfigValue_shouldReturnEmptyForNull() {
        assertThat(StorageEngineEnum.fromConfigValue(null)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "MINIO,    Minio",
            "ALIYUN_OSS, 阿里云OSS",
            "TENCENT_COS, 腾讯云COS",
            "HUAWEI_OBS, 华为云OBS",
            "AWS_S3,   AWS S3",
            "KSYUN_KS3, 金山云KS3",
            "VOLCENGINE_TOS, 火山引擎TOS",
            "AZURE_BLOB, 微软Azure Blob",
            "FTP,      FTP",
            "SFTP,     SFTP",
            "SMB,      SMB",
            "LOCAL,    本地存储"
    })
    void eachEnum_shouldExposeDescriptionAndConfigValue(String name, String description) {
        StorageEngineEnum engine = StorageEngineEnum.valueOf(name);
        assertThat(engine.getDescription()).isEqualTo(description);
        assertThat(engine.getConfigValue()).isNotBlank();
    }

    @Test
    void validConfigValues_shouldContainAllConfigValues() {
        String valid = StorageEngineEnum.validConfigValues();
        for (StorageEngineEnum e : StorageEngineEnum.values()) {
            assertThat(valid).contains(e.getConfigValue());
        }
    }

    @Test
    void validConfigValues_shouldBeCommaSeparated() {
        String valid = StorageEngineEnum.validConfigValues();
        assertThat(valid.split(", ")).hasSize(12);
    }

    @Test
    void objectStorageFlag_shouldMatchDeclaredValue() {
        assertThat(StorageEngineEnum.MINIO.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.ALIYUN_OSS.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.TENCENT_COS.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.HUAWEI_OBS.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.AWS_S3.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.KSYUN_KS3.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.VOLCENGINE_TOS.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.AZURE_BLOB.isObjectStorage()).isTrue();
        assertThat(StorageEngineEnum.FTP.isObjectStorage()).isFalse();
        assertThat(StorageEngineEnum.SFTP.isObjectStorage()).isFalse();
        assertThat(StorageEngineEnum.SMB.isObjectStorage()).isFalse();
        assertThat(StorageEngineEnum.LOCAL.isObjectStorage()).isFalse();
    }

    @Test
    void configValue_shouldBeLowercaseUnderscore() {
        for (StorageEngineEnum e : StorageEngineEnum.values()) {
            String value = e.getConfigValue();
            assertThat(value).matches("[a-z0-9_]+");
        }
    }

    @ParameterizedTest
    @EnumSource(StorageEngineEnum.class)
    void roundTrip_fromConfigValue_thenGetName(StorageEngineEnum engine) {
        Optional<StorageEngineEnum> resolved = StorageEngineEnum.fromConfigValue(engine.getConfigValue());
        assertThat(resolved).contains(engine);
    }

    @Test
    void configValues_globallyUnique() {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (StorageEngineEnum e : StorageEngineEnum.values()) {
            assertThat(seen.add(e.getConfigValue()))
                    .as("duplicate configValue: %s on %s", e.getConfigValue(), e)
                    .isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(StorageEngineEnum.class)
    void enumNameAndConfigValue_shouldNotCollide(StorageEngineEnum engine) {
        assertThat(engine.name()).isNotEqualTo(engine.getConfigValue());
    }

    @ParameterizedTest
    @EnumSource(StorageEngineEnum.class)
    void objectStorageFlag_shouldBeConsistentWithFromConfigValue(StorageEngineEnum engine) {
        StorageEngineEnum resolved = StorageEngineEnum.fromConfigValue(engine.getConfigValue()).orElseThrow();
        assertThat(resolved.isObjectStorage()).isEqualTo(engine.isObjectStorage());
    }
}
