package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class ObsAclTypeConverterTest {

    private final ObsAclTypeConverter converter = new ObsAclTypeConverter();

    @ParameterizedTest
    @EnumSource(AclTypeEnum.class)
    void convert_supportedAclTypes(AclTypeEnum aclType) {
        assertThat(converter.convertToEngineAcl(aclType)).isNotNull();
    }

    @Test
    void getSupportedEngine_isDefined() {
        assertThat(converter.getSupportedEngine()).isNotNull();
    }
}
