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
