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
package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObsStorageTypeConverterTest {

    private final ObsStorageTypeConverter converter = new ObsStorageTypeConverter();

    @ParameterizedTest
    @EnumSource(value = StorageTypeEnum.class, names = {
            "STANDARD", "STANDARD_IA", "ARCHIVE", "COLD_ARCHIVE", "DEEP_COLD_ARCHIVE",
            "INTELLIGENT_TIERING"
    })
    void convert_supportedTypes(StorageTypeEnum type) {
        assertThat(converter.convertToEngineType(type)).isNotBlank();
    }

    @Test
    void getSupportedEngine_isDefined() {
        assertThat(converter.getSupportedEngine()).isNotNull();
    }

    @Test
    void convert_unsupportedType_throws() {
        assertThatThrownBy(() -> converter.convertToEngineType(StorageTypeEnum.ONEZONE_IA))
                .isInstanceOf(StorageTypeUnsupportedException.class);
    }
}
