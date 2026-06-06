package com.richie.component.storage.converter;

import com.richie.component.storage.enums.AclTypeEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinioAclTypeConverterTest {

    private final MinioAclTypeConverter converter = new MinioAclTypeConverter();

    @ParameterizedTest
    @EnumSource(value = AclTypeEnum.class, names = {"PRIVATE", "PUBLIC_READ", "PUBLIC_READ_WRITE", "AUTHENTICATED_READ", "BUCKET_OWNER_READ", "BUCKET_OWNER_FULL_CONTROL", "LOG_DELIVERY_WRITE"})
    void convert_supportedAclTypes(AclTypeEnum aclType) {
        assertThat(converter.convertToEngineAcl(aclType)).isNotNull();
    }

    @Test
    void getSupportedEngine_isDefined() {
        assertThat(converter.getSupportedEngine()).isNotNull();
    }

    @Test
    void convert_logDeliveryWrite_mapsToEngineValue() {
        assertThat(converter.convertToEngineAcl(AclTypeEnum.LOG_DELIVERY_WRITE)).isEqualTo("log-delivery-write");
    }
}
