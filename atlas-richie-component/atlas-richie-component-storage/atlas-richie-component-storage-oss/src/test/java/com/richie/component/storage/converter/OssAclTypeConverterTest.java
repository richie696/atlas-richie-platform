package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssAclTypeConverterTest {

    private final OssAclTypeConverter converter = new OssAclTypeConverter();

    @ParameterizedTest
    @EnumSource(value = AclTypeEnum.class, names = {
            "PRIVATE", "PUBLIC_READ", "PUBLIC_READ_WRITE", "AUTHENTICATED_READ",
            "BUCKET_OWNER_READ", "BUCKET_OWNER_FULL_CONTROL"
    })
    void convert_supportedAclTypes(AclTypeEnum aclType) {
        assertThat(converter.convertToEngineAcl(aclType)).isNotNull();
    }

    @Test
    void getSupportedEngine_isDefined() {
        assertThat(converter.getSupportedEngine()).isNotNull();
    }

    @Test
    void convert_unsupportedAcl_throws() {
        assertThatThrownBy(() -> converter.convertToEngineAcl(AclTypeEnum.LOG_DELIVERY_WRITE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
