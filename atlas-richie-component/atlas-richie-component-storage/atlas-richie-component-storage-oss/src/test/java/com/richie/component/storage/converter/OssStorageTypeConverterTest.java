package com.richie.component.storage.converter;

import com.richie.component.storage.enums.StorageTypeEnum;
import com.richie.component.storage.exception.StorageTypeUnsupportedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssStorageTypeConverterTest {

    private final OssStorageTypeConverter converter = new OssStorageTypeConverter();

    @ParameterizedTest
    @EnumSource(value = StorageTypeEnum.class, names = {
            "STANDARD", "STANDARD_IA", "ARCHIVE", "COLD_ARCHIVE", "DEEP_COLD_ARCHIVE"
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
        assertThatThrownBy(() -> converter.convertToEngineType(StorageTypeEnum.INTELLIGENT_TIERING))
                .isInstanceOf(StorageTypeUnsupportedException.class);
    }
}
